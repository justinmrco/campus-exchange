package com.campusexchange.tools;

import com.campusexchange.CampusExchangeApplication;
import com.campusexchange.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import javax.sql.DataSource;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Explicit local demo data only. Never invoked by application startup. */
public final class DemoSeed {
    private static final String[] USERS = {"demo_admin", "demo_maya", "demo_alex", "demo_leo", "demo_nora"};
    private static final Path LOCAL_FILE = Path.of("config/demo-accounts.properties");
    private record Item(String category, String title, String price, String description) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("DemoSeed takes no arguments; configure the local database first.");
        SpringApplication app = new SpringApplication(CampusExchangeApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run(); Connection c = context.getBean(DataSource.class).getConnection()) {
            boolean created = seed(c, LOCAL_FILE);
            System.out.println(created
                    ? "Created 5 demo accounts, 43 listings (36 available), 8 exchanges and 2 disputes."
                    : "Demo records already exist. No accounts, passwords or records were changed.");
            System.out.println("Local demo credentials and record references: " + LOCAL_FILE.toAbsolutePath());
        } catch (Exception error) {
            System.err.println("Demo seeding did not finish normally. Existing records are never reset. If a commit was attempted, rerun to inspect the saved references before retrying; keep the local credential file.");
            throw error;
        }
    }

    /** Requires an idle auto-commit connection; the manifest is saved before the database commit. */
    public static boolean seed(Connection c, Path file) throws Exception {
        if (!c.getAutoCommit()) throw new IllegalArgumentException("Use an idle auto-commit connection.");
        file = file.toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another seed command is using this credential file.");
            List<Item> items = items();
            Properties p = new Properties();
            boolean saved = Files.exists(file);
            if (saved) {
                try (var in = Files.newInputStream(file)) { p.load(in); }
                if (!"campusexchange-demo-v1".equals(p.getProperty("format"))) throw new IllegalStateException("Unrecognized credential file; it was not overwritten.");
                if (!Objects.equals(c.getCatalog(), p.getProperty("database")) || !c.getMetaData().getURL().equals(p.getProperty("url"))) throw new IllegalStateException("This credential file belongs to a different database.");
            }
            int existing = 0;
            for (String username : USERS) {
                try (PreparedStatement query = c.prepareStatement("SELECT user_id,role FROM users WHERE username=?")) {
                    query.setString(1, username);
                    try (ResultSet r = query.executeQuery()) {
                        if (!r.next()) continue;
                        existing++;
                        if (!saved || !Integer.toString(r.getInt(1)).equals(p.getProperty(username + ".id")) || !role(username).equals(r.getString(2))) throw new IllegalStateException("A reserved demo username already exists. No account was adopted or overwritten: " + username);
                    }
                }
            }
            if (existing > 0) {
                if (existing != USERS.length || !referencesExist(c, p, "listings", "listing_id", 43) || !referencesExist(c, p, "handover_ledger", "ledger_id", 8) || !referencesExist(c, p, "disputes", "dispute_id", 2)) throw new IllegalStateException("Demo references are incomplete. Existing records were preserved; inspect the local manifest.");
                return false;
            }
            if (!saved) {
                p.setProperty("format", "campusexchange-demo-v1");
                p.setProperty("database", c.getCatalog());
                p.setProperty("url", c.getMetaData().getURL());
                SecureRandom random = new SecureRandom();
                for (String username : USERS) {
                    byte[] bytes = new byte[12]; random.nextBytes(bytes);
                    p.setProperty(username + ".password", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
                }
            }
            // A failed attempt can reuse its saved credentials, but never creates partial records.
            for (String username : USERS) {
                String password = p.getProperty(username + ".password");
                if (password == null || password.length() < 8 || password.length() > 128) throw new IllegalStateException("Invalid saved demo credentials.");
                p.remove(username + ".id");
            }
            p.setProperty("state", "prepared");
            save(file, p);
            c.setAutoCommit(false);
            try {
                LocalDateTime now = LocalDateTime.now(Validation.ZONE).withNano(0);
                PasswordUtil passwords = new PasswordUtil();
                int[] members = new int[4];
                for (int i = 0; i < USERS.length; i++) {
                    String username = USERS[i];
                    int id = insert(c, "INSERT INTO users(username,password_hash,role,created_at) VALUES(?,?,?,?)", username, passwords.hash(p.getProperty(username + ".password")), role(username), now.minusDays(90 - i * 7));
                    p.setProperty(username + ".id", Integer.toString(id));
                    if (i > 0) members[i - 1] = id;
                }
                List<Integer> listingIds = new ArrayList<>(), ledgerIds = new ArrayList<>(), disputeIds = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) listingIds.add(listing(c, members[i % 4], items.get(i), "AVAILABLE", now.minusHours(2 + i * 5L)));
                p.setProperty("available.ids", ids(listingIds));
                String[] states = {"RESERVED", "MEETUP_PROPOSED", "MEETUP_ARRANGED", "VERIFYING", "COMPLETED", "DISPUTED", "DISPUTED"};
                Item[] history = {
                    new Item("Textbooks", "Linear algebra study copy", "45.00", "A clean introductory linear algebra textbook. This fictional demo exchange is waiting for a meetup proposal."),
                    new Item("Electronics", "USB-C docking station", "120.00", "A used docking station with HDMI and USB ports. This fictional demo exchange has a meetup proposal awaiting a response."),
                    new Item("Furniture", "Compact bedside shelf", "65.00", "A small wooden shelf with two tiers and a few surface marks. This fictional demo exchange has an agreed meetup."),
                    new Item("Stationery", "Technical drawing set", "38.00", "A complete compass and ruler set in its original case. This fictional demo exchange has one participant confirmation remaining."),
                    new Item("Bicycles", "Campus commuter bicycle", "280.00", "A used city bicycle with a basket and working brakes. The fictional demo handover for this listing has been completed."),
                    new Item("Other", "Badminton racket pair", "55.00", "Two used badminton rackets. This fictional demo exchange is paused for an administrator to review a condition disagreement."),
                    new Item("Clothing", "Lightweight rain jacket", "45.00", "A used medium rain jacket. This fictional demo exchange was withdrawn after both participants agreed not to proceed.")
                };
                TokenUtil tokens = new TokenUtil();
                for (int i = 0; i < states.length; i++) {
                    int seller = members[i % 4], buyer = members[(i + 1) % 4];
                    String state = states[i], listingState = i == 4 ? "COMPLETED" : i == 5 ? "DISPUTED" : i == 6 ? "INACTIVE" : "RESERVED";
                    int listingId = listing(c, seller, history[i], listingState, now.minusDays(12)); listingIds.add(listingId);
                    LocalDateTime created = i < 4 ? now.minusHours(3 + i * 2L) : now.minusDays(i == 4 ? 6 : i == 5 ? 3 : 10);
                    LocalDateTime meetup = i == 0 ? null : i == 1 ? now.plusDays(2).withHour(14).withMinute(0).withSecond(0) : i == 2 ? now.plusHours(3) : i == 3 ? now.minusHours(1) : i == 4 ? now.minusDays(4) : i == 5 ? now.minusDays(2) : now.minusDays(8);
                    boolean confirmed = i >= 2;
                    int version = i == 0 ? 0 : i == 1 ? 1 : i == 2 ? 2 : i == 4 || i == 6 ? 4 : 3;
                    LocalDateTime updated = i < 4 ? now.minusMinutes(20) : i == 6 ? now.minusDays(7) : meetup.plusHours(1);
                    int ledger = insert(c, "INSERT INTO handover_ledger(listing_id,buyer_id,seller_id,transaction_state,version,meetup_location,meetup_time,proposed_by,buyer_token,seller_token,buyer_verified,seller_verified,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", listingId, buyer, seller, state, version, meetup == null ? null : "Main library entrance", meetup, i == 1 ? buyer : null, confirmed ? tokens.generateToken() : null, confirmed ? tokens.generateToken() : null, i == 3 || i == 4, i == 4, created, updated);
                    ledgerIds.add(ledger); p.setProperty("scenario." + (i == 5 ? "DISPUTED_OPEN" : i == 6 ? "DISPUTED_RESOLVED" : state), Integer.toString(ledger));
                    if (i >= 5) {
                        int dispute = insert(c, "INSERT INTO disputes(ledger_id,reporter_id,reason,status,admin_notes,created_at,resolved_at) VALUES(?,?,?,?,?,?,?)", ledger, buyer,
                                i == 5 ? "Fictional demo case: the buyer reports that one racket grip is more worn than described. The exchange is paused while both participants provide their account." : "Fictional demo case: the jacket fit did not match the buyer's expectations. Both participants agreed to stop the exchange.",
                                i == 5 ? "OPEN" : "RESOLVED", i == 5 ? null : "Demo resolution: both members agreed not to proceed. The listing was withdrawn and the transaction history retained. No platform payment or refund was involved.", meetup.plusMinutes(30), i == 5 ? null : updated);
                        disputeIds.add(dispute);
                    }
                }
                int cancelledListing = listingIds.get(17), cancelledSeller = members[17 % 4], cancelledBuyer = members[(17 + 1) % 4];
                int cancelled = insert(c, "INSERT INTO handover_ledger(listing_id,buyer_id,seller_id,transaction_state,version,created_at,updated_at) VALUES(?,?,?,'CANCELLED',1,?,?)", cancelledListing, cancelledBuyer, cancelledSeller, now.minusDays(2), now.minusDays(1));
                ledgerIds.add(cancelled); p.setProperty("scenario.CANCELLED", Integer.toString(cancelled));
                p.setProperty("listings.ids", ids(listingIds)); p.setProperty("handover_ledger.ids", ids(ledgerIds)); p.setProperty("disputes.ids", ids(disputeIds));
                p.setProperty("created.at", now.toString());
                // If the commit result is uncertain, these IDs allow a repeat run to inspect instead of duplicate.
                save(file, p);
                var photos=DemoPhotos.catalog();
                if(!photos.isEmpty())DemoPhotos.attach(c,p,photos);
                c.commit();
            } catch (Exception error) {
                try { c.rollback(); c.setAutoCommit(true); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
            c.setAutoCommit(true);
            p.setProperty("state", "committed"); save(file, p);
            return true;
        }
    }

    private static String role(String username) { return username.equals("demo_admin") ? "ADMIN" : "USER"; }
    private static String ids(List<Integer> values) { return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }

    private static boolean referencesExist(Connection c, Properties p, String table, String key, int expected) throws SQLException {
        String values = p.getProperty(table + ".ids", "");
        if (!values.matches("[1-9][0-9]*(,[1-9][0-9]*)*") || values.split(",").length != expected) return false;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + key + " IN (" + values + ")")) { return r.next() && r.getInt(1) == expected; }
    }

    private static int listing(Connection c, int seller, Item item, String state, LocalDateTime created) throws SQLException {
        return insert(c, "INSERT INTO listings(seller_id,title,description,price,category,status,created_at) VALUES(?,?,?,?,?,?,?)", seller, Validation.text(item.title(), 1, 100, "Title"), Validation.text(item.description(), 1, 5000, "Description"), Validation.price(item.price()), Validation.category(item.category()), state, created);
    }

    private static int insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            if (p.executeUpdate() != 1) throw new SQLException("Demo insert affected an unexpected number of records.");
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
            throw new SQLException("Demo insert did not return an ID.");
        }
    }

    private static List<Item> items() throws IOException {
        try (InputStream in = DemoSeed.class.getResourceAsStream("/demo-listings.tsv")) {
            if (in == null) throw new IOException("Demo inventory resource is missing.");
            List<Item> result = new ArrayList<>(); Map<String, Integer> counts = new HashMap<>(); Set<String> titles = new HashSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) throw new IOException("Invalid demo inventory row.");
                Item item = new Item(Validation.category(fields[0]), Validation.text(fields[1], 1, 100, "Title"), Validation.price(fields[2]).toPlainString(), Validation.text(fields[3], 1, 5000, "Description"));
                if (!titles.add(item.title())) throw new IOException("Duplicate demo title.");
                counts.merge(item.category(), 1, Integer::sum); result.add(item);
            }
            if (!counts.equals(Map.of("Textbooks", 8, "Electronics", 6, "Furniture", 5, "Clothing", 4, "Stationery", 4, "Bicycles", 4, "Other", 5))) throw new IOException("Unexpected demo category distribution.");
            return result;
        }
    }

    private static void save(Path file, Properties p) throws IOException {
        Path temp = Files.createTempFile(file.getParent(), "demo-accounts-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "LOCAL DEMO CREDENTIALS - do not publish. Keep this recovery manifest if a seed attempt fails."); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
