package com.campusexchange.service;

import com.campusexchange.dao.ListingDAO;
import com.campusexchange.entity.Listing;
import com.campusexchange.util.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import org.springframework.web.multipart.MultipartFile;
import javax.sql.DataSource;
import java.util.List;

@Service
public class ListingService {
    @Autowired private ListingDAO listingDAO;
    @Autowired private Workflow workflow;
    @Autowired private ImageStore images;
    @Autowired private DataSource dataSource;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ListingService.class);
    // A locking read waits for any in-flight commit before deleting an unreferenced file.
    private void cleanup(String reference){
        if(reference==null||!reference.startsWith("/media/listings/"))return;
        try(var c=dataSource.getConnection()){c.setAutoCommit(false);try(var p=c.prepareStatement("SELECT listing_id FROM listings WHERE image_path=? FOR UPDATE")){p.setQueryTimeout(5);p.setString(1,reference);try(var r=p.executeQuery()){if(!r.next())images.delete(reference);}}c.commit();}
        catch(Exception error){log.warn("Unused photo cleanup deferred; file retained for safety.");}
    }

    public Listing createListing(int seller, String title, String description, String price, String category) throws SQLException {return createListing(seller,title,description,price,category,null);}
    public Listing createListing(int seller, String title, String description, String price, String category, MultipartFile photo) throws SQLException {
        Listing listing = new Listing();
        listing.setSellerId(seller);
        listing.setTitle(Validation.text(title, 1, 100, "Title"));
        listing.setDescription(Validation.text(description, 0, 5000, "Description"));
        listing.setPrice(Validation.price(price));
        listing.setCategory(Validation.category(category));
        listing.setStatus(Listing.STATUS_AVAILABLE);
        listing.setImagePath(images.save(photo));
        try{listing.setListingId(workflow.transaction(c -> {
            Workflow.active(c, seller);
            return listingDAO.insert(listing, c);
        }));}catch(SQLException|RuntimeException error){cleanup(listing.getImagePath());throw error;}
        return listing;
    }

    private Listing editable(Connection c, int id, int seller) throws SQLException {
        Listing listing = listingDAO.lockForUpdate(id, c);
        if (listing == null) throw new IllegalArgumentException("Listing not found.");
        if (listing.getSellerId() != seller) throw new IllegalArgumentException("You do not own this listing.");
        if (!listing.isAvailable()) throw new IllegalStateException("Only available listings can be edited or withdrawn.");
        Workflow.active(c, seller);
        return listing;
    }

    public void editListing(int id, int seller, String title, String description, String price, String category) throws SQLException {editListing(id,seller,title,description,price,category,null,false);}
    public void editListing(int id, int seller, String title, String description, String price, String category, MultipartFile photo, boolean removePhoto) throws SQLException {
        if(removePhoto&&photo!=null&&!photo.isEmpty())throw new IllegalArgumentException("Choose either a replacement photo or Remove photo, not both.");
        String cleanTitle = Validation.text(title, 1, 100, "Title");
        String cleanDescription = Validation.text(description, 0, 5000, "Description");
        var cleanPrice = Validation.price(price);
        String cleanCategory = Validation.category(category);
        String replacement=images.save(photo);
        String old;
        try{old=workflow.transaction(c -> {
            Listing listing = editable(c, id, seller);
            String previous=listing.getImagePath();
            if(removePhoto||replacement!=null)listing.setImagePath(removePhoto?"":replacement);
            listing.setTitle(cleanTitle);
            listing.setDescription(cleanDescription);
            listing.setPrice(cleanPrice);
            listing.setCategory(cleanCategory);
            listingDAO.updateWithinTransaction(listing, c);
            return previous;
        });}catch(SQLException|RuntimeException error){cleanup(replacement);throw error;}
        if(removePhoto||replacement!=null)cleanup(old);
    }

    public void deleteListing(int id, int seller) throws SQLException {
        workflow.transaction(c -> {
            editable(c, id, seller);
            listingDAO.softDeleteWithinTransaction(id, seller, c);
            return null;
        });
    }

    public Listing getListingById(int id) throws SQLException { return listingDAO.findById(id); }
    public List<Listing> browseAvailable(String keyword, String category) throws SQLException { return listingDAO.findAvailable(keyword, category); }
    public List<Listing> getMyListings(int seller) throws SQLException { return listingDAO.findBySeller(seller); }
    public int countListings() throws SQLException { return listingDAO.countAll(); }
    public List<Listing> getAllListings() throws SQLException { return listingDAO.findAll(); }
}
