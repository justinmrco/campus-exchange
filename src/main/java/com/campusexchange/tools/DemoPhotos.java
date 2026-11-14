package com.campusexchange.tools;

import com.campusexchange.CampusExchangeApplication;
import org.springframework.boot.*;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.sql.*;
import java.util.*;

/** Explicit attachment only; no downloads, schema changes or resets on startup. */
public final class DemoPhotos {
 public static Map<String,String> catalog() throws Exception {
  var result=new LinkedHashMap<String,String>();var names=new HashSet<String>();
  try(var in=DemoPhotos.class.getResourceAsStream("/demo-images.tsv")){
   if(in==null)throw new IllegalStateException("Missing demo image catalog.");
   for(String row:new String(in.readAllBytes(),StandardCharsets.UTF_8).split("\\R")){
    if(row.isBlank()||row.startsWith("#"))continue;
    String[] v=row.split("\t",-1);
    if(v.length!=5||Arrays.stream(v).anyMatch(String::isBlank)||!v[1].matches("[a-z0-9-]+\\.jpg"))throw new IllegalStateException("Invalid demo photo or missing provenance.");
    URI source=URI.create(v[2]);boolean web=Set.of("https","http").contains(source.getScheme())&&source.getHost()!=null;
    boolean local=v[2].matches("local:productimage/[a-z0-9-]+\\.jpg");
    if(!web&&!local)throw new IllegalStateException("Invalid image source reference.");
    if(result.put(v[0],"/images/demo/"+v[1])!=null||!names.add(v[1]))throw new IllegalStateException("Duplicate demo title or image.");
    try(var image=DemoPhotos.class.getResourceAsStream("/static/images/demo/"+v[1])){
     if(image==null)throw new IllegalStateException("Demo photo file is missing: "+v[1]);
     try(var stream=ImageIO.createImageInputStream(image)){
      var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalStateException("Invalid demo photo.");var reader=readers.next();
      try{reader.setInput(stream);if(!reader.getFormatName().equalsIgnoreCase("JPEG")||reader.getWidth(0)>1600||reader.getHeight(0)>1600)throw new IllegalStateException("Demo images must be JPEG, at most 1600 pixels per side.");reader.read(0).flush();}finally{reader.dispose();}
     }
    }
   }
  }
  if(!result.isEmpty()&&result.size()!=43)throw new IllegalStateException("Expected 43 documented demo photos; none were attached.");
  return result;
 }
 public static int attach(Connection c,Properties manifest,Map<String,String> photos)throws Exception{
  if(c.getAutoCommit())throw new IllegalStateException("Use an explicit transaction.");
  if(photos.size()!=43)throw new IllegalStateException("43 documented local photos are required; acquisition is incomplete. No images were attached.");
  if(!"campusexchange-demo-v1".equals(manifest.getProperty("format"))||!Objects.equals(c.getCatalog(),manifest.getProperty("database"))||!c.getMetaData().getURL().equals(manifest.getProperty("url")))throw new IllegalStateException("Demo manifest belongs to another database.");
  Set<Integer> sellers=new HashSet<>();
  for(String user:List.of("demo_maya","demo_alex","demo_leo","demo_nora")){
   int id=Integer.parseInt(manifest.getProperty(user+".id"));
   try(var p=c.prepareStatement("SELECT username FROM users WHERE user_id=?")){p.setInt(1,id);try(var r=p.executeQuery()){if(!r.next()||!user.equals(r.getString(1)))throw new IllegalStateException("Demo account identity changed.");}}
   sellers.add(id);
  }
  String ids=manifest.getProperty("listings.ids","");if(!ids.matches("[1-9][0-9]*(,[1-9][0-9]*){42}"))throw new IllegalStateException("Expected 43 saved listing IDs.");
  Set<String> seen=new HashSet<>();int changed=0;
  for(String id:ids.split(","))try(var p=c.prepareStatement("SELECT seller_id,title,image_path FROM listings WHERE listing_id=? FOR UPDATE")){
   p.setInt(1,Integer.parseInt(id));
   try(var r=p.executeQuery()){
    if(!r.next()||!sellers.contains(r.getInt(1))||!photos.containsKey(r.getString(2))||!seen.add(r.getString(2)))throw new IllegalStateException("Saved demo listing identity changed.");
    String photo=photos.get(r.getString(2)),old=r.getString(3);
    // Never overwrite a member's replacement photo or removal on repeat execution.
    if(old!=null){continue;}
    try(var update=c.prepareStatement("UPDATE listings SET image_path=? WHERE listing_id=? AND image_path IS NULL")){update.setString(1,photo);update.setInt(2,Integer.parseInt(id));changed+=update.executeUpdate();}
   }
  }
  return changed;
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=0)throw new IllegalArgumentException("DemoPhotos takes no arguments.");
  Map<String,String> photos=catalog();if(photos.isEmpty())throw new IllegalStateException("Photo downloads are incomplete: no real product images are bundled. No database changes performed.");
  Properties manifest=new Properties();try(var in=Files.newInputStream(Path.of("config/demo-accounts.properties"))){manifest.load(in);}
  SpringApplication app=new SpringApplication(CampusExchangeApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
  try(var context=app.run();var c=context.getBean(DataSource.class).getConnection()){
   c.setAutoCommit(false);try{int changed=attach(c,manifest,photos);c.commit();System.out.println("Attached "+changed+" demo photos; existing photos were preserved.");}catch(Exception error){c.rollback();throw error;}
  }
 }
}
