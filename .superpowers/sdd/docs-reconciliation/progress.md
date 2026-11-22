# SDD ledger — plan: CleanSaver docs reconciliation (in-conversation plan, m0015+m0031)

Ruling: TDD not applicable to markdown prose edits — no executable tests exist for doc content; verification = the plan's grep checks run against real output — cost if wrong: a missed conflict could ship undetected (mitigated by grep + full re-read at review).
Ruling: work directly on main in a fresh empty repo — the repo has zero commits, plan specifies a single initial commit on main, user approved push — cost if wrong: none; nothing shared exists yet.
