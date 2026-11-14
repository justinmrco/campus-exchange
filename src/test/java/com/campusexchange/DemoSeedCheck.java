package com.campusexchange;

import com.campusexchange.tools.DemoSeed;
import com.campusexchange.service.AuthService;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Included in AcceptanceCheck's disposable database, never the application database. */
final class DemoSeedCheck {
    private static int count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); return r.getInt(1); }
    }
    private static Properties load(Path file) throws Exception {
        Properties p = new Properties(); try (var in = Files.newInputStream(file)) { p.load(in); } return p;
    }
    static void run(DataSource db, AuthService auth) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "demo-seed-check-");
        Path file = directory.resolve("demo-accounts.properties");
        String[] tables = {"users", "listings", "handover_ledger", "disputes"};
        int[] before = new int[4];
        try (Connection c = db.getConnection()) {
            for (int i = 0; i < tables.length; i++) before[i] = count(c, tables[i]);
            try (Statement s = c.createStatement()) { s.execute("CREATE TRIGGER demo_seed_rollback_check BEFORE INSERT ON listings FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected demo rollback check'"); }
            try {
                try { DemoSeed.seed(c, file); throw new AssertionError("Injected seed failure must reject"); }
                catch (SQLException expected) { AcceptanceCheck.check(Files.exists(file), "Recovery credentials saved before inserts"); }
                for (int i = 0; i < tables.length; i++) AcceptanceCheck.check(count(c, tables[i]) == before[i], "Seed failure rolled back " + tables[i]);
            } finally { try (Statement s = c.createStatement()) { s.execute("DROP TRIGGER demo_seed_rollback_check"); } }
            String originalPassword = load(file).getProperty("demo_admin.password");
            AcceptanceCheck.check(DemoSeed.seed(c, file), "Seed succeeds after rollback");
            Properties p = load(file);
            AcceptanceCheck.check(originalPassword.equals(p.getProperty("demo_admin.password")), "Retry retains saved credentials");
            int[] added = {5, 43, 8, 2};
            for (int i = 0; i < tables.length; i++) AcceptanceCheck.check(count(c, tables[i]) == before[i] + added[i], "Seed count " + tables[i]);
            AcceptanceCheck.check(!DemoSeed.seed(c, file), "Repeat seed is a no-op");
            for (int i = 0; i < tables.length; i++) AcceptanceCheck.check(count(c, tables[i]) == before[i] + added[i], "Repeat did not duplicate " + tables[i]);
            try { DemoSeed.seed(c, directory.resolve("unrelated.properties")); throw new AssertionError("Existing usernames must not be adopted"); }
            catch (IllegalStateException expected) { AcceptanceCheck.check(!Files.exists(directory.resolve("unrelated.properties")), "Collision does not create replacement credentials"); }
            Map<String,Integer> categories = new HashMap<>();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT category,COUNT(*) FROM listings WHERE status='AVAILABLE' AND listing_id IN (" + p.getProperty("available.ids") + ") GROUP BY category")) { while (r.next()) categories.put(r.getString(1), r.getInt(2)); }
            AcceptanceCheck.check(categories.equals(Map.of("Textbooks",8,"Electronics",6,"Furniture",5,"Clothing",4,"Stationery",4,"Bicycles",4,"Other",5)), "Available catalog category counts");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM handover_ledger h JOIN listings l ON l.listing_id=h.listing_id WHERE h.ledger_id IN (" + p.getProperty("handover_ledger.ids") + ") AND ((h.transaction_state IN ('RESERVED','MEETUP_PROPOSED','MEETUP_ARRANGED','VERIFYING') AND l.status='RESERVED') OR (h.transaction_state='COMPLETED' AND l.status='COMPLETED' AND h.buyer_verified=TRUE AND h.seller_verified=TRUE) OR (h.transaction_state='CANCELLED' AND l.status='AVAILABLE') OR (h.transaction_state='DISPUTED' AND l.status IN ('DISPUTED','INACTIVE')))")) { r.next(); AcceptanceCheck.check(r.getInt(1)==8,"Seeded exchange/listing consistency"); }
            for (String username : List.of("demo_admin","demo_maya","demo_alex","demo_leo","demo_nora")) {
                var account = auth.login(username, p.getProperty(username + ".password"));
                AcceptanceCheck.check(account != null && account.isAdmin() == username.equals("demo_admin"), "Demo login role " + username);
                AcceptanceCheck.client = AcceptanceCheck.browser();
                String csrf = AcceptanceCheck.field(AcceptanceCheck.get("/login").body(), "_csrf");
                var response = AcceptanceCheck.post("/login", Map.of("username",username,"password",p.getProperty(username+".password"),"_csrf",csrf));
                AcceptanceCheck.check(response.statusCode()==302,"Demo HTTP login");
                if (account.isAdmin()) {
                    AcceptanceCheck.page("/admin/dashboard"); AcceptanceCheck.page("/admin/disputes");
                    for (String scenario : List.of("RESERVED","MEETUP_PROPOSED","MEETUP_ARRANGED","VERIFYING","COMPLETED","CANCELLED","DISPUTED_OPEN","DISPUTED_RESOLVED")) {
                        String route="/handover/ledger/"+p.getProperty("scenario."+scenario);
                        AcceptanceCheck.page(route);
                        String html=AcceptanceCheck.get(route).body();
                        AcceptanceCheck.check(html.contains("compact-header"), "Compact task masthead");
                        if(scenario.startsWith("DISPUTED")) AcceptanceCheck.check(html.contains("progress-interruption") && !html.contains("<ol class=\"timeline\""),"Dispute interrupts progress");
                        else if(!scenario.equals("CANCELLED")) AcceptanceCheck.check(html.contains("is-complete") || html.contains("is-current"),"State-aware progress");
                        if(scenario.equals("MEETUP_ARRANGED")) AcceptanceCheck.check(html.contains("Meetup confirmed"),"Readable status label");
                    }
                    AcceptanceCheck.check(!AcceptanceCheck.get("/handover/ledger/"+p.getProperty("scenario.MEETUP_ARRANGED")).body().contains("name=\"submittedCode\""),"Demo admin has no participant form");
                } else {
                    for(String route:List.of("/marketplace","/marketplace?page=1","/my-listings","/handover/transactions","/dispute/my-disputes")) AcceptanceCheck.page(route);
                    AcceptanceCheck.check(AcceptanceCheck.get("/admin/users").statusCode()==403,"Demo member cannot administer");
                }
            }
            for (String category : categories.keySet()) AcceptanceCheck.page("/marketplace?category="+category);
            Map<String,String> photos=new HashMap<>();
            try(var query=c.createStatement();var rows=query.executeQuery("SELECT title FROM listings WHERE listing_id IN ("+p.getProperty("listings.ids")+")")){int index=0;while(rows.next())photos.put(rows.getString(1),"/images/demo/test-"+(index++)+".jpg");}
            c.setAutoCommit(false);
            try{
                try(var clear=c.createStatement()){clear.executeUpdate("UPDATE listings SET image_path=NULL WHERE listing_id IN ("+p.getProperty("listings.ids")+")");}
                AcceptanceCheck.check(com.campusexchange.tools.DemoPhotos.attach(c,p,photos)==43,"Photo attachment covers 43 saved demo listings");
                AcceptanceCheck.check(com.campusexchange.tools.DemoPhotos.attach(c,p,photos)==0,"Photo attachment repeat makes no changes");
                try(var query=c.createStatement()){query.executeUpdate("UPDATE listings SET image_path='' WHERE listing_id="+p.getProperty("listings.ids").split(",")[0]);}
                AcceptanceCheck.check(com.campusexchange.tools.DemoPhotos.attach(c,p,photos)==0,"Explicit photo removal survives repeat import");
                String title=photos.keySet().iterator().next();photos.put("Wrong title",photos.remove(title));
                try{com.campusexchange.tools.DemoPhotos.attach(c,p,photos);throw new AssertionError("Changed listing identity must stop import");}catch(IllegalStateException expected){}
            }finally{c.rollback();c.setAutoCommit(true);}

        } finally {
            try (var files=Files.list(directory)) { for(Path entry:files.toList()) Files.delete(entry); }
            Files.delete(directory);
        }
        System.out.println("PASS: demo rollback/retry, username collision protection, repeat execution, inventory counts, all demo logins, roles and populated pages.");
    }
}
