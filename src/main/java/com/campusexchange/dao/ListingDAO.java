package com.campusexchange.dao;

import com.campusexchange.entity.Listing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ListingDAO {

    @Autowired
    private DataSource dataSource;

    public int insert(Listing listing) throws SQLException {
        try(Connection c=dataSource.getConnection()){return insert(listing,c);}
    }
    public int insert(Listing listing,Connection conn)throws SQLException {
        String sql = "INSERT INTO listings (seller_id, title, description, price, category, status, image_path) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, listing.getSellerId());
            ps.setString(2, listing.getTitle());
            ps.setString(3, listing.getDescription());
            ps.setBigDecimal(4, listing.getPrice());
            ps.setString(5, listing.getCategory());
            ps.setString(6, Listing.STATUS_AVAILABLE);
            ps.setString(7,listing.getImagePath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert listing.");
    }

    public Listing findById(int listingId) throws SQLException {
        String sql = "SELECT l.listing_id, l.seller_id, u.username AS seller_username, " +
                     "l.title, l.description, l.price, l.category, l.status, l.image_path, l.created_at " +
                     "FROM listings l JOIN users u ON l.seller_id = u.user_id " +
                     "WHERE l.listing_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * PESSIMISTIC LOCK: Lock the listing row within an existing transaction.
     * Caller must pass an active Connection with autoCommit = false.
     *
     * This is the core of concurrency safety:
     * SELECT ... FOR UPDATE blocks other transactions from reading or modifying
     * this row until the current transaction commits or rolls back.
     */
    public Listing lockForUpdate(int listingId, Connection conn) throws SQLException {
        String sql = "SELECT listing_id, seller_id, title, description, price, category, status, image_path, created_at " +
                     "FROM listings WHERE listing_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Listing l = new Listing();
                    l.setListingId(rs.getInt("listing_id"));
                    l.setSellerId(rs.getInt("seller_id"));
                    l.setTitle(rs.getString("title"));
                    l.setDescription(rs.getString("description"));
                    l.setPrice(rs.getBigDecimal("price"));
                    l.setCategory(rs.getString("category"));
                    l.setStatus(rs.getString("status"));
        l.setImagePath(rs.getString("image_path"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) l.setCreatedAt(ts.toLocalDateTime());
                    return l;
                }
            }
        }
        return null;
    }




    public void updateWithinTransaction(Listing listing, Connection conn) throws SQLException {
        String sql = "UPDATE listings SET title = ?, description = ?, price = ?, category = ?, image_path = ? " +
                     "WHERE listing_id = ? AND seller_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getTitle());
            ps.setString(2, listing.getDescription());
            ps.setBigDecimal(3, listing.getPrice());
            ps.setString(4, listing.getCategory());
            ps.setString(5,listing.getImagePath());
            ps.setInt(6, listing.getListingId());
            ps.setInt(7, listing.getSellerId());
            ps.executeUpdate();
        }
    }


    public void softDeleteWithinTransaction(int listingId, int sellerId, Connection conn) throws SQLException {
        String sql = "UPDATE listings SET status = 'INACTIVE' WHERE listing_id = ? AND seller_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.setInt(2, sellerId);
            ps.executeUpdate();
        }
    }

    public List<Listing> findAvailable(String keyword, String category) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT l.listing_id, l.seller_id, u.username AS seller_username, " +
            "l.title, l.description, l.price, l.category, l.status, l.image_path, l.created_at " +
            "FROM listings l JOIN users u ON l.seller_id = u.user_id " +
            "WHERE l.status = 'AVAILABLE' AND u.active=TRUE");

        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (l.title LIKE ? OR l.description LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND l.category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY l.created_at DESC,l.listing_id DESC"+com.campusexchange.util.Pagination.limit());

        List<Listing> listings = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) listings.add(mapRow(rs));
            }
        }
        return listings;
    }

    public List<Listing> findBySeller(int sellerId) throws SQLException {
        String sql = "SELECT l.listing_id, l.seller_id, u.username AS seller_username, " +
                     "l.title, l.description, l.price, l.category, l.status, l.image_path, l.created_at " +
                     "FROM listings l JOIN users u ON l.seller_id = u.user_id " +
                     "WHERE l.seller_id = ? ORDER BY l.created_at DESC,l.listing_id DESC" + com.campusexchange.util.Pagination.limit();
        List<Listing> listings = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) listings.add(mapRow(rs));
            }
        }
        return listings;
    }

    public List<Listing> findAll() throws SQLException {
        String sql = "SELECT l.listing_id, l.seller_id, u.username AS seller_username, " +
                     "l.title, l.description, l.price, l.category, l.status, l.image_path, l.created_at " +
                     "FROM listings l JOIN users u ON l.seller_id = u.user_id " +
                     "ORDER BY l.created_at DESC,l.listing_id DESC" + com.campusexchange.util.Pagination.limit();
        List<Listing> listings = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) listings.add(mapRow(rs));
        }
        return listings;
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM listings";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Listing mapRow(ResultSet rs) throws SQLException {
        Listing l = new Listing();
        l.setListingId(rs.getInt("listing_id"));
        l.setSellerId(rs.getInt("seller_id"));
        l.setSellerUsername(rs.getString("seller_username"));
        l.setTitle(rs.getString("title"));
        l.setDescription(rs.getString("description"));
        l.setPrice(rs.getBigDecimal("price"));
        l.setCategory(rs.getString("category"));
        l.setStatus(rs.getString("status"));
        l.setImagePath(rs.getString("image_path"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) l.setCreatedAt(ts.toLocalDateTime());
        return l;
    }
}
