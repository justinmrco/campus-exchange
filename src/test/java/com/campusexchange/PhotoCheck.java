package com.campusexchange;
import com.campusexchange.service.*;
import com.campusexchange.tools.DemoPhotos;
import org.springframework.web.multipart.MultipartFile;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class PhotoCheck {
 record Photo(byte[] data,String type) implements MultipartFile {
  public String getName(){return "photo";}public String getOriginalFilename(){return "../../fake.svg";}public String getContentType(){return type;}public boolean isEmpty(){return data.length==0;}public long getSize(){return data.length;}public byte[] getBytes(){return data;}public InputStream getInputStream(){return new ByteArrayInputStream(data);}public void transferTo(File file)throws IOException{Files.write(file.toPath(),data);}
 }
 static byte[] png(int w,int h)throws Exception{var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);image.flush();return out.toByteArray();}
 static Path file(ImageStore store,String ref){return store.resolve(ref.substring(ref.lastIndexOf('/')+1));}
 static HttpResponse<String> multipart(String route,Map<String,String> fields,byte[] photo)throws Exception{
  String boundary="ce-"+UUID.randomUUID();var out=new ByteArrayOutputStream();
  for(var field:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
  out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"demo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(photo);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
  return AcceptanceCheck.client.send(HttpRequest.newBuilder(URI.create(AcceptanceCheck.base+route)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
 }
 static void run(DataSource db,ListingService listings,AuthService auth,ImageStore store)throws Exception{
  var owner=auth.register("photo_owner","password123","password123");var other=auth.register("photo_other","password123","password123");
  byte[] valid=png(2000,1000);Photo photo=new Photo(valid,"application/octet-stream");
  AcceptanceCheck.rejects(()->store.save(new Photo("<svg/>".getBytes(),"image/jpeg")));
  AcceptanceCheck.rejects(()->store.save(new Photo(new byte[5*1024*1024+1],"image/png")));
  AcceptanceCheck.rejects(()->store.save(new Photo(png(6001,1),"image/png")));
  AcceptanceCheck.rejects(()->store.save(new Photo(png(5000,4100),"image/png")));
  AcceptanceCheck.rejects(()->store.resolve("../../config/application-local.properties"));
  var listing=listings.createListing(owner.getUserId(),"Photo test","Test photo","25","Other",photo);
  String first=listings.getListingById(listing.getListingId()).getImageUrl();
  AcceptanceCheck.check(first!=null&&first.matches("/media/listings/[a-f0-9-]{36}\\.jpg"),"Generated local reference");
  var image=ImageIO.read(file(store,first).toFile());AcceptanceCheck.check(image.getWidth()==1600&&image.getHeight()==800,"Photo normalized to longest edge");image.flush();
  try(var c=db.getConnection();var statement=c.createStatement()){
   statement.execute("CREATE TRIGGER photo_insert_failure BEFORE INSERT ON listings FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Photo rollback test'");
   try{try{listings.createListing(owner.getUserId(),"Fail","Test","20","Other",photo);throw new AssertionError("Injected insert must fail");}catch(java.sql.SQLException expected){}}
   finally{statement.execute("DROP TRIGGER photo_insert_failure");}
  }
  try(var files=Files.list(file(store,first).getParent())){AcceptanceCheck.check(files.count()==1,"Failed save cleans unreferenced photo");}
  listings.editListing(listing.getListingId(),owner.getUserId(),"Photo test","Test photo","25","Other");AcceptanceCheck.check(first.equals(listings.getListingById(listing.getListingId()).getImagePath()),"Empty file keeps image");
  AcceptanceCheck.rejects(()->listings.editListing(listing.getListingId(),other.getUserId(),"No","","25","Other",photo,false));
  AcceptanceCheck.check(Files.exists(file(store,first)),"Unauthorized edit preserves original image");
  AcceptanceCheck.rejects(()->listings.editListing(listing.getListingId(),owner.getUserId(),"No","","25","Other",photo,true));
  listings.editListing(listing.getListingId(),owner.getUserId(),"Photo test","Test photo","25","Other",photo,false);
  String second=listings.getListingById(listing.getListingId()).getImagePath();AcceptanceCheck.check(!first.equals(second)&&!Files.exists(file(store,first)),"Replacement cleans old unreferenced image");
  listings.editListing(listing.getListingId(),owner.getUserId(),"Photo test","Test photo","25","Other",null,true);AcceptanceCheck.check(listings.getListingById(listing.getListingId()).getImageUrl()==null&&!Files.exists(file(store,second)),"Removal clears photo and cleans file");
  AcceptanceCheck.client=AcceptanceCheck.browser();String csrf=AcceptanceCheck.field(AcceptanceCheck.get("/login").body(),"_csrf");AcceptanceCheck.post("/login",Map.of("username","photo_owner","password","password123","_csrf",csrf));
  String form=AcceptanceCheck.get("/listing/create").body();csrf=AcceptanceCheck.field(form,"_csrf");String token=AcceptanceCheck.field(form,"formToken");
  Map<String,String> fields=new HashMap<>(Map.of("title","Multipart photo","description","Test","price","20","category","Other","formToken",token));
  AcceptanceCheck.check(multipart("/listing/create",fields,valid).statusCode()==403,"Multipart CSRF remains required");fields.put("_csrf",csrf);
  AcceptanceCheck.check(multipart("/listing/create",fields,valid).statusCode()==302,"Multipart upload accepted with CSRF");
  var created=listings.getMyListings(owner.getUserId()).stream().filter(l->l.getTitle().equals("Multipart photo")).findFirst().orElseThrow();
  AcceptanceCheck.check(created.getImageUrl()!=null,"HTTP upload persisted image");
  var response=AcceptanceCheck.get(created.getImageUrl());AcceptanceCheck.check(response.statusCode()==200&&response.headers().firstValue("Content-Type").orElse("").startsWith("image/jpeg"),"Local image served as JPEG");
  AcceptanceCheck.check(AcceptanceCheck.get("/listing/"+created.getListingId()).body().contains(created.getImageUrl()),"Detail renders local photo");
  AcceptanceCheck.check(multipart("/listing/create",fields,valid).statusCode()==302,"Multipart retry redirects");
  AcceptanceCheck.check(listings.getMyListings(owner.getUserId()).stream().filter(l->l.getTitle().equals("Multipart photo")).count()==1,"Multipart retry does not duplicate");
  AcceptanceCheck.check(multipart("/listing/"+created.getListingId()+"/edit",fields,new byte[5*1024*1024+1]).statusCode()==413,"Oversized multipart gives a useful 413");
  if(DemoPhotos.catalog().isEmpty())AcceptanceCheck.rejects(()->DemoPhotos.main(new String[0]));
  System.out.println("PASS: image validation, dimensions, local paths, normalization, retention/replacement/removal, ownership, multipart CSRF, serving and duplicate protection.");
 }
}
