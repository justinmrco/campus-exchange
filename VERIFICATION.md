# Release verification

Updated on 15 November 2026 on Windows with JDK 25, targeting Java 23.

## Passed in the final audit

- Fresh compilation of main and test Java sources through the JDK JavaCompiler API.
- Full acceptance program against a randomly named, disposable MySQL database. The test database and temporary uploads were removed after success; application records were not modified by this run.
- Password hashing, legacy upgrades, validation, competing reservations, stale/self proposal rejection, cancellation/acceptance and verification/dispute races, reciprocal handover and rollback.
- CSRF, roles, session/account deactivation, duplicate submissions, attempt limits, live snapshots and rendered member/admin pages.
- Demo seed rollback/retry, username collisions, safe repeat execution, inventory totals, all five demo logins and populated pages.
- Image content/dimension validation, path restrictions, normalization, replacement/removal, ownership, multipart CSRF, serving and duplicate protection. The populated 43-photo catalog is exercised by the seed checks.
- JavaScript syntax and runnable motion/interaction checks: first-visit playback, replay prevention, reduced motion, unavailable storage, native transitions, history restoration, pending buttons, persistent update notices and broken-photo fallback.
- Compact item and exchange layouts preserve participant restrictions, CSRF, proposal versions and original routes. Rejected counter-proposals reopen their editor with escaped draft values; the acceptance suite checks this behavior and unchanged ledger version.
- Shared receipt-style confirmation checks passed for cancellation, Escape/backdrop dismissal, safe text, focus return, preserved submitter, one-shot approval, duplicate prevention, fallback and unsaved-change refresh.
- Independent local Git repository initialized on main with the supplied campus-exchange remote. Private configuration, generated credentials, original photos, uploads, build/IDE output and research drafts are ignored.

## Evidence and remaining limitations

- **Build evidence:** the owner supplied a successful normal clean verify log on 15 November 2026. After the UI refinements, Maven test compilation again encountered Windows dependency-JAR access restrictions here. Fresh main/test compilation through the JDK compiler API and the full acceptance suite passed; WAR packaging passed separately with Maven test compilation skipped. No permanent test-skipping configuration was added. The acceptance main is run separately from Maven verify.
- **Dependency advisories:** a fresh online advisory scan has not completed; no zero-vulnerability claim is made.
- **Browser coverage:** owner-provided desktop screenshots cover classifieds, posting, item detail, proposal/acceptance, reciprocal code verification, completion, exchange history, administrator overview and open/resolved disputes. The captured areas were visually reviewed. Some captures predate the latest width/spacing refinements. Automated native-browser inspection remained unavailable; screenshots do not prove keyboard behavior or all viewport sizes. Mobile screenshots and visual acceptance were intentionally deferred by the owner.
- **Photo rights:** original sources, creators and licenses for the 43 owner-supplied photographs remain unverified. The owner requested their inclusion as noncommercial demonstration assets. README.md and the photo credits state that this does not establish redistribution permission. No ownership or license claim is made.
- **Remote repository:** the local repository targets https://github.com/justinmrco/campus-exchange.git. Earlier pushes from this environment failed on connectivity; confirm the final push result separately.
- **Legacy migrations:** fresh-schema acceptance does not establish compatibility with an arbitrary existing database. Back up and inspect existing tables before running migration scripts.

## Reproduce

1. Follow README.md for JDK, MySQL and local configuration.
2. Run mvn clean verify to package the WAR.
3. Run com.campusexchange.AcceptanceCheck from the test classpath with the project root as working directory and a local test account allowed to create/drop disposable databases and create triggers.
4. Optionally run node --check src/main/resources/static/js/site.js and node src/test/js/paper-check.cjs.
5. Manually inspect browse/search/filter/pagination, listing detail, forms, exchanges and admin pages at desktop and phone widths. Check keyboard focus and reduced motion.

Local seed/attachment commands are explicit tools, never startup behavior. Member uploads and private demo passwords are not part of the repository. Product photos are representative; exact condition, editions and accessories are not guaranteed by the illustrations.
