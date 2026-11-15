package com.campusexchange;
import com.campusexchange.service.*;
import com.campusexchange.dao.*;
import com.campusexchange.entity.*;
import com.campusexchange.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import javax.sql.DataSource;
import java.sql.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
/** Run explicitly: creates and drops ONLY a random campusexchange_test_* database. */
public final class AcceptanceCheck {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 @FunctionalInterface interface Action{void run()throws Exception;}
 static void rejects(Action a)throws Exception{try{a.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Expected rejection");}
 static String base;static HttpClient client;
 static HttpClient browser(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 static HttpResponse<String> get(String path)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 static String field(String html,String name){var m=java.util.regex.Pattern.compile("name=\""+name+"\"[^>]*value=\"([^\"]*)").matcher(html);if(!m.find())throw new AssertionError("Missing field "+name);return m.group(1);}
 static HttpResponse<String> post(String path,Map<String,String> fields)throws Exception{String data=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(data)).build(),HttpResponse.BodyHandlers.ofString());}
 static void page(String path)throws Exception{var r=get(path);check(r.statusCode()==200,"Page failed: "+path+" status "+r.statusCode());check(r.body().contains("/js/site.js"),"Missing script "+path);}
 public static void main(String[] args)throws Exception{
  var props=new Properties();if(Files.exists(Path.of("config/application-local.properties")))try(var in=Files.newInputStream(Path.of("config/application-local.properties"))){props.load(in);}
  String url=System.getenv().getOrDefault("DB_URL",props.getProperty("spring.datasource.url","jdbc:mysql://localhost:3306/campusexchange?serverTimezone=Asia/Shanghai"));String user=System.getenv().getOrDefault("DB_USERNAME",props.getProperty("spring.datasource.username","campusexchange"));String password=System.getenv().getOrDefault("DB_PASSWORD",props.getProperty("spring.datasource.password",""));
  check(url!=null&&url.startsWith("jdbc:mysql://"),"Configure MySQL first");String host=url.substring(0,url.indexOf('/',13)+1);String name="campusexchange_test_"+UUID.randomUUID().toString().replace("-","");check(name.matches("campusexchange_test_[a-f0-9]{32}"),"Unsafe test database");
  try(Connection admin=DriverManager.getConnection(host,user,password)){
   try(Statement s=admin.createStatement()){s.executeUpdate("CREATE DATABASE "+name);}
   try{
    String testUrl=host+name+"?serverTimezone=Asia/Shanghai";
    // Setup scripts may select the real database. Execute only table DDL in the isolated catalog.
    var tables=new ArrayList<String>();
    for(String statement:Files.readString(Path.of("database/schema.sql")).split(";")){
     String sql=statement.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").trim();
     if(sql.isBlank())continue;
     String normalized=sql.toUpperCase(Locale.ROOT);
     if(normalized.startsWith("CREATE DATABASE ")||normalized.startsWith("USE ")||normalized.equals("SHOW TABLES"))continue;
     if(!sql.matches("(?is)CREATE\\s+TABLE\\s+(users|listings|handover_ledger|disputes)\\s*\\(.*"))throw new IllegalArgumentException("Unsupported test schema statement");
     tables.add(sql);
    }
    check(tables.size()==4,"Expected four table definitions");
    try(Connection c=DriverManager.getConnection(testUrl,user,password);Statement statement=c.createStatement()){check(name.equals(c.getCatalog()),"Wrong test catalog");for(String sql:tables)statement.execute(sql);}
    var app=new SpringApplication(CampusExchangeApplication.class);
    try(var context=app.run("--server.port=0","--app.upload-directory=target/"+name+"-photos","--spring.datasource.url="+testUrl,"--spring.datasource.username="+user,"--spring.datasource.password="+password,"--spring.devtools.restart.enabled=false","--logging.level.root=WARN")){
     base="http://localhost:"+((WebServerApplicationContext)context).getWebServer().getPort();client=browser();
     var auth=context.getBean(AuthService.class);var listings=context.getBean(ListingService.class);var reserve=context.getBean(ReservationService.class);var handover=context.getBean(HandoverService.class);var disputes=context.getBean(DisputeService.class);var db=context.getBean(DataSource.class);
     var hashing=new PasswordUtil();String hash=hashing.hash("password123");check(hashing.verify("password123",hash)&&!hashing.verify("wrong",hash),"PBKDF2");byte[] salt=new byte[16];Arrays.fill(salt,(byte)7);var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(salt);String legacy=Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(digest.digest("password123".getBytes(StandardCharsets.UTF_8)));check(hashing.verify("password123",legacy),"Legacy verification");
     rejects(()->Validation.price("0"));rejects(()->Validation.price("1.001"));rejects(()->Validation.category(null));rejects(()->Validation.future("2000-01-01T10:00"));
     User seller=auth.register("seller","password123","password123"),buyer=auth.register("buyer","password123","password123"),other=auth.register("other","password123","password123"),administrator=auth.register("admin","password123","password123");
     try(Connection c=db.getConnection()){Workflow.update(c,"UPDATE users SET role='ADMIN' WHERE user_id=?",administrator.getUserId());Workflow.update(c,"UPDATE users SET password_hash=? WHERE user_id=?",legacy,other.getUserId());}check(auth.login("other","password123")!=null,"Legacy login");check(context.getBean(UserDAO.class).findById(other.getUserId()).getPasswordHash().startsWith("pbkdf2:"),"Legacy upgrade");
     Listing item=listings.createListing(seller.getUserId(),"Calculus","Good condition","15.00","Textbooks");
     ExecutorService pool=Executors.newFixedThreadPool(2);try{var latch=new CountDownLatch(1);var results=pool.invokeAll(List.of(()->{latch.countDown();try{reserve.reserve(item.getListingId(),buyer);return 1;}catch(IllegalStateException e){return 0;}},()->{latch.await();try{reserve.reserve(item.getListingId(),other);return 1;}catch(IllegalStateException e){return 0;}}));check((Integer)results.get(0).get()+(Integer)results.get(1).get()==1,"Exactly one reservation");}finally{pool.shutdownNow();}
     HandoverLedger ledger=handover.getLedgerByListingId(item.getListingId());int participant=ledger.getBuyerId();String time=LocalDateTime.now(Validation.ZONE).plusHours(2).withSecond(0).withNano(0).toString();handover.proposeMeetup(ledger.getLedgerId(),seller.getUserId(),"Library",time,0);rejects(()->handover.acceptMeetup(ledger.getLedgerId(),participant,0));rejects(()->handover.acceptMeetup(ledger.getLedgerId(),seller.getUserId(),1));handover.acceptMeetup(ledger.getLedgerId(),participant,1);rejects(()->reserve.cancelReservation(ledger.getLedgerId(),participant));
     var ready=handover.getLedgerById(ledger.getLedgerId());TokenUtil tokens=new TokenUtil();check("WAITING".equals(handover.submitMyCode(ready.getLedgerId(),participant,tokens.toDisplayCode(ready.getSellerToken()))),"First verification");check("COMPLETED".equals(handover.submitMyCode(ready.getLedgerId(),seller.getUserId(),tokens.toDisplayCode(ready.getBuyerToken()))),"Completion");check(listings.getListingById(item.getListingId()).getStatus().equals("COMPLETED"),"Atomic listing completion");check("COMPLETED".equals(handover.submitMyCode(ready.getLedgerId(),seller.getUserId(),tokens.toDisplayCode(ready.getBuyerToken()))),"Idempotent confirmation");
     Listing second=listings.createListing(seller.getUserId(),"Lamp","Works","10","Other");var contested=reserve.reserve(second.getListingId(),buyer);var dispute=disputes.fileDispute(contested.getLedgerId(),buyer.getUserId(),"Item differs");check(disputes.getMyDisputes(seller.getUserId()).size()==1,"Seller sees dispute");rejects(()->disputes.fileDispute(contested.getLedgerId(),seller.getUserId(),"Again"));rejects(()->disputes.resolveDispute(dispute.getDisputeId(),buyer.getUserId(),"No"));disputes.resolveDispute(dispute.getDisputeId(),administrator.getUserId(),"Exchange withdrawn");check(listings.getListingById(second.getListingId()).getStatus().equals("INACTIVE"),"Resolution listing");
     var workflow=context.getBean(Workflow.class);try{workflow.transaction(c->{Workflow.update(c,"UPDATE listings SET title='bad' WHERE listing_id=?",item.getListingId());throw new SQLException("rollback check");});}catch(SQLException expected){}check(listings.getListingById(item.getListingId()).getTitle().equals("Calculus"),"Rollback");
     page("/login");page("/register");check(post("/login",Map.of("username","seller","password","password123")).statusCode()==403,"CSRF enforced");String csrf=field(get("/login").body(),"_csrf");check(post("/login",Map.of("username","seller","password","password123","_csrf",csrf)).statusCode()==302,"Login");
     for(String route:List.of("/marketplace","/marketplace?page=1&keyword=x","/my-listings","/listing/create","/listing/"+item.getListingId(),"/listing/"+item.getListingId()+"/edit","/handover/transactions","/handover/ledger/"+ledger.getLedgerId(),"/handover/ledger/"+contested.getLedgerId(),"/dispute/my-disputes"))page(route);
     String create=get("/listing/create").body();String formToken=field(create,"formToken");String formCsrf=field(create,"_csrf");var createFields=Map.of("title","HTTP listing","description","From form","price","6.00","category","Other","_csrf",formCsrf,"formToken",formToken);int countBefore=listings.countListings();check(post("/listing/create",createFields).statusCode()==302,"Create form submission");check(post("/listing/create",createFields).statusCode()==302&&listings.countListings()==countBefore+1,"Duplicate create prevented");
     Listing third=listings.createListing(seller.getUserId(),"Notes","Clean","2","Stationery");var pending=reserve.reserve(third.getListingId(),buyer);page("/handover/ledger/"+pending.getLedgerId());check(get("/handover/ledger/"+pending.getLedgerId()).body().contains("name=\"version\""),"Version form");page("/dispute/file/"+pending.getLedgerId());handover.proposeMeetup(pending.getLedgerId(),buyer.getUserId(),"Gate",time,0);page("/handover/ledger/"+pending.getLedgerId());
     String proposalPage=get("/handover/ledger/"+pending.getLedgerId()).body();
     check(!proposalPage.matches("(?s).*<details[^>]* open(?:=|>).*"),"Secondary proposal form starts collapsed");
     check(post("/handover/propose/"+pending.getLedgerId(),Map.of("location","Draft & gate","meetupTime","2000-01-01T10:00","version","1","_csrf",field(proposalPage,"_csrf"))).statusCode()==302,"Invalid counter-proposal redirects");
     String recovered=get("/handover/ledger/"+pending.getLedgerId()).body();
     check(recovered.matches("(?s).*<details[^>]* open(?:=|>).*"),"Invalid proposal reopens editor");
     check(recovered.contains("value=\"Draft &amp; gate\"")&&recovered.contains("value=\"2000-01-01T10:00\""),"Proposal draft retained and escaped");
     check(handover.getLedgerById(pending.getLedgerId()).getVersion()==1,"Rejected proposal preserves version");
     handover.acceptMeetup(pending.getLedgerId(),seller.getUserId(),1);page("/handover/ledger/"+pending.getLedgerId());check(get("/admin/users").statusCode()==403,"Admin restriction");
     // Cancellation and acceptance must serialize on the same listing/ledger.
     Listing raceItem=listings.createListing(seller.getUserId(),"Race","","3","Other");var race=reserve.reserve(raceItem.getListingId(),buyer);handover.proposeMeetup(race.getLedgerId(),buyer.getUserId(),"Gate",time,0);
     ExecutorService racePool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);var futures=racePool.invokeAll(List.of(()->{gate.countDown();try{reserve.cancelReservation(race.getLedgerId(),buyer.getUserId());return 1;}catch(IllegalStateException e){return 0;}},()->{gate.await();try{handover.acceptMeetup(race.getLedgerId(),seller.getUserId(),1);return 1;}catch(IllegalStateException e){return 0;}}));check((Integer)futures.get(0).get()+(Integer)futures.get(1).get()==1,"Cancel/accept one winner");}finally{racePool.shutdownNow();}
     var finalRace=handover.getLedgerById(race.getLedgerId());check(listings.getListingById(raceItem.getListingId()).getStatus().equals("CANCELLED".equals(finalRace.getTransactionState())?"AVAILABLE":"RESERVED"),"Cancel/accept consistency");
     var readyRace=handover.getLedgerById(pending.getLedgerId());handover.submitMyCode(readyRace.getLedgerId(),buyer.getUserId(),tokens.toDisplayCode(readyRace.getSellerToken()));
     ExecutorService verifyPool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);var futures=verifyPool.invokeAll(List.of(()->{gate.countDown();try{handover.submitMyCode(readyRace.getLedgerId(),seller.getUserId(),tokens.toDisplayCode(readyRace.getBuyerToken()));return 1;}catch(IllegalStateException e){return 0;}},()->{gate.await();try{disputes.fileDispute(readyRace.getLedgerId(),buyer.getUserId(),"Race report");return 1;}catch(IllegalStateException e){return 0;}}));check((Integer)futures.get(0).get()+(Integer)futures.get(1).get()==1,"Verify/dispute one winner");}finally{verifyPool.shutdownNow();}
     var verifiedRace=handover.getLedgerById(pending.getLedgerId());check(listings.getListingById(third.getListingId()).getStatus().equals(verifiedRace.isCompleted()?"COMPLETED":"DISPUTED"),"Verify/dispute consistency");
     client=browser();csrf=field(get("/login").body(),"_csrf");post("/login",Map.of("username","admin","password","password123","_csrf",csrf));for(String route:List.of("/admin/dashboard","/admin/users","/admin/listings","/admin/transactions","/admin/disputes","/admin/disputes/"+dispute.getDisputeId(),"/handover/ledger/"+pending.getLedgerId()))page(route);check(!get("/handover/ledger/"+pending.getLedgerId()).body().contains("name=\"submittedCode\""),"Admin cannot verify others");
     try{auth.deleteUser(administrator.getUserId());throw new AssertionError("Admin deactivation");}catch(SQLException expected){}
     csrf=field(get("/admin/users").body(),"_csrf");check(post("/admin/users/"+other.getUserId()+"/deactivate",Map.of("_csrf",csrf)).statusCode()==302,"Deactivation form endpoint");check(auth.login("other","password123")==null,"HTTP deactivation applied");
     client=browser();csrf=field(get("/login").body(),"_csrf");post("/login",Map.of("username","buyer","password","password123","_csrf",csrf));page("/marketplace");
     auth.deleteUser(buyer.getUserId());check(get("/marketplace").statusCode()==302,"Existing session invalidated by deactivation");check(auth.login("buyer","password123")==null,"Inactive login");check(handover.getLedgerById(pending.getLedgerId())!=null,"History retained");
     client=browser();csrf=field(get("/register").body(),"_csrf");check(post("/register",Map.of("username","fresh","password","password123","confirmPassword","password123","_csrf",csrf)).statusCode()==302,"Registration");page("/marketplace");check(get("/marketplace?page=-1").statusCode()==400,"Invalid pagination rejected");check(get("/logout").statusCode()==405,"GET logout rejected");
     try(var stream=client.send(HttpRequest.newBuilder(URI.create(base+"/events/marketplace")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofInputStream()).body();var lines=new java.io.BufferedReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8))){String line;boolean snapshot=false;for(int i=0;i<20&&(line=lines.readLine())!=null;i++){if(line.contains("revision")){snapshot=true;break;}}check(snapshot,"SSE revision snapshot");}
     client=browser();csrf=field(get("/login").body(),"_csrf");for(int i=0;i<30;i++)post("/login",Map.of("username","missing-account","password","wrong","_csrf",csrf));var limited=post("/login",Map.of("username","missing-account","password","wrong","_csrf",csrf));check(limited.statusCode()==429&&limited.headers().firstValue("Retry-After").orElse("").equals("600"),"Login attempt limit");
     DemoSeedCheck.run(db,auth);
     PhotoCheck.run(db,listings,auth,context.getBean(com.campusexchange.service.ImageStore.class));
     System.out.println("PASS: passwords, validation, reservation race, versions, handover, disputes, rollback, CSRF, roles, deactivation and rendered pages.");
    }
   }finally{try(Statement s=admin.createStatement()){s.executeUpdate("DROP DATABASE "+name);}Path photos=Path.of("target",name+"-photos");if(Files.exists(photos)){try(var entries=Files.list(photos)){for(Path entry:entries.toList())Files.delete(entry);}Files.delete(photos);}
    System.out.println("Temporary test database and uploads removed.");}
  }
 }
}
