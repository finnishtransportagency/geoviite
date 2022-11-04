# Repeatable migrations
- These will be run after all versioned migrations
- If the files change, they will be run again
  - They need to be idempotent
  - Note, that while they will be run in-order on the first run, any edited file will be run alone
