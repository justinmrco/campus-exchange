# Release verification

Checked on 14 November 2026 on Windows with JDK 25, targeting Java 23.

## Passed in the final audit

- Fresh compilation of main and test Java sources through the JDK JavaCompiler API.
- Full acceptance program against a randomly named, disposable MySQL database. The test database and temporary uploads were removed after success; application records were not modified by this run.
- Password hashing, legacy upgrades, validation, competing reservations, stale/self proposal rejection, cancellation/acceptance and verification/dispute races, reciprocal handover and rollback.
- CSRF, roles, session/account deactivation, duplicate submissions, attempt limits, live snapshots and rendered member/admin pages.
- Demo seed rollback/retry, username collisions, safe repeat execution, inventory totals, all five demo logins and populated pages.
- Image content/dimension validation, path restrictions, normalization, replacement/removal, ownership, multipart CSRF, serving and duplicate protection. The populated 43-photo catalog is exercised by the seed checks.
- JavaScript syntax and runnable motion/interaction checks: first-visit playback, replay prevention, reduced motion, unavailable storage, native transitions, history restoration, pending buttons, persistent update notices and broken-photo fallback.
- Independent local Git repository initialized on main with the supplied campus-exchange remote. Private configuration, generated credentials, original photos, uploads, build/IDE output and research drafts are ignored.

## Remaining release limitations

- **Normal Maven WAR build:** the isolated offline Maven verify attempt failed during test compilation while accessing spring-boot-sql-4.0.6.jar, with Windows access-denied output. Fresh compilation and acceptance execution passed through the JDK compiler API, but this does not verify WAR packaging. Run mvn clean verify in a normal terminal or IntelliJ Maven before publishing a build artifact. The database acceptance main is separate from Maven verify.
- **Dependency advisories:** a fresh online advisory scan has not completed; no zero-vulnerability claim is made.
- **Browser acceptance:** native browser inspection stopped because the current URL could not be verified. HTTP rendering and scripted behavior passed. Six owner-provided desktop screenshots were visually reviewed: marketplace masthead, multiple classifieds rows and the posting form show consistent editorial styling with no obvious overlap in the captured areas. Screenshots do not verify interactive behavior; mobile layout and keyboard interaction still need a manual browser check.
- **Photo rights:** the 43 owner-supplied photos have unverified original sources, creators and licenses. See src/main/resources/static/images/demo/CREDITS.md. Supplying an image does not itself establish redistribution permission. Resolve rights before a public push that includes these assets.
- **Remote repository:** network access to GitHub failed in this environment. Remote contents and authentication have not been verified; nothing was pushed.
- **Legacy migrations:** fresh-schema acceptance does not establish compatibility with an arbitrary existing database. Back up and inspect existing tables before running migration scripts.

## Reproduce

1. Follow README.md for JDK, MySQL and local configuration.
2. Run mvn clean verify to package the WAR.
3. Run com.campusexchange.AcceptanceCheck from the test classpath with the project root as working directory and a local test account allowed to create/drop disposable databases and create triggers.
4. Optionally run node --check src/main/resources/static/js/site.js and node src/test/js/paper-check.cjs.
5. Manually inspect browse/search/filter/pagination, listing detail, forms, exchanges and admin pages at desktop and phone widths. Check keyboard focus and reduced motion.

Local seed/attachment commands are explicit tools, never startup behavior. Member uploads and private demo passwords are not part of the repository. Product photos are representative; exact condition, editions and accessories are not guaranteed by the illustrations.
