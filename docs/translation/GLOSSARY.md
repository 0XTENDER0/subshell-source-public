# Subshell glossary

Words that come back across the whole app. Pick one translation for each before
translating the file, and use it everywhere: a reader sees the same thing called
the same way on every screen. `tools/translation-consistency.py` checks a
translation against this table (see "What we check" in `TRANSLATING.md`).

How to fill it in: write your choice in your language's column. Give the forms
the checker should accept, separated by commas, as the start of a word
anywhere in a word (`publicaci` accepts publicación and publicaciones, and a compound counts). Leave a cell empty if a term
needs no fixed choice. The Norwegian column is a worked example; the cells hold only the forms, notes go in the meaning column.

"Count" is how many English strings use the term (2026-11-17), so the first rows
matter most.

| English | Count | Meaning in Subshell | nb | es-419 |
|---|---|---|---|---|
| post, posts | 387 | A Reddit submission (noun). Also the verb "Post" on a button. | innlegg, publiser, legg ut, legge ut | |
| community, communities | 271 | A subreddit (r/name). Reddit's own word; "subreddit" is rare in the app. | fellesskap | |
| comment, comments | 230 | A comment under a post. | komment | |
| account | 193 | A signed-in Reddit account on this phone. | konto | |
| moderator | 105 | A community's moderator ("mod" in compounds below). | moderator | |
| hidden, hide | 88 | Hidden from the reader's lists (local or on Reddit). | skjul, skjult | |
| video | 87 | | video | |
| saved | 81 | A post or comment the reader saved on Reddit (Saved list). | lagre, lagret | |
| reply, replies | 72 | An answer to a comment or message. | svar | |
| feed | 69 | A list of posts (Home, Popular, a community). | feed | |
| mute, muted, unmute | 68 | Silencing a community or chat for a while. Not the same as hide or block. | demp | |
| permission | 67 | Android or Reddit permission (what the app or account may do). | tillatelse | |
| media | 67 | Images, GIFs and videos together. | medie | |
| authorize, authorization | 63 | Giving an app access to the Reddit account. nb: never "autorisasjon". | gi tilgang, tilgang | |
| link | 59 | A web address. | lenke | |
| backup | 55 | Subshell's settings backup file. | sikkerhetskopi | |
| remove | 53 | A moderator removing a post or comment; also removing an item from a list. | fjern | |
| message | 52 | A private message or chat message. | melding | |
| flair | 49 | Reddit's label on a post or user. Many languages keep "flair". nb: never inflected ("denne flair"). | flair | |
| sign in, sign-in | 49 | Logging in to Reddit. | logg, innlogging | |
| Reddit returned | 49 | Start of many error messages: what Reddit's server answered. | Reddit sendte | |
| image | 47 | | bilde | |
| chat, conversation | 44 | Reddit chat (direct messages). | chat, samtale | |
| You | 42 | The tab with the reader's own profile and lists. | Du | |
| users, people | 40 | People found by search, or users in a community. nb: never "personer". | bruker | |
| report | 39 | Reporting a post or comment to moderators. | rapport | |
| NSFW | 39 | Not safe for work. Keep "NSFW". | NSFW | |
| profile | 39 | A Reddit user's profile page. | profil | |
| Modmail | 38 | Reddit's moderator mail. Keep "Modmail". | Modmail | |
| update | 34 | A new version of the app. | oppdater | |
| blocked, block | 30 | Blocking a Reddit user. | blokker | |
| Inbox | 29 | The tab with messages, replies and notifications. | Innboks | |
| draft | 29 | Unsent text kept for later. | utkast | |
| New | 27 | The sort order "New" (newest first) and "new" posts. | ny, nye | |
| Wiki | 26 | A community's wiki pages. | wiki | |
| Client ID | 26 | The Reddit app id the reader sets up once. Keep "Client ID". | Client ID | |
| All | 25 | The feed r/all, and "all" in general. | alle | |
| crosspost | 22 | Sharing a post into another community. | krysspost | |
| vote, upvote, downvote | 20 | Reddit's voting. | stem | |
| Settings | 18 | The app's settings screen. | innstilling | |
| custom feed | 18 | A feed the reader builds from several communities. | egen feed | |
| Home | 18 | The Home feed (the reader's communities). | Hjem | |
| note | 17 | A private note or a moderator's internal note. | notat | |
| lock | 17 | Locking a thread (no new comments) or locking the app. | lås | |
| restore | 16 | Restoring a backup. | gjenopprett | |
| thread | 15 | A post with its comments. | tråd | |
| gallery | 13 | A post with several images. | galleri | |
| spoiler | 13 | Content covered until tapped. | spoiler | |
| swipe | 13 | Swiping a post or comment sideways. | sveip | |
| offline | 12 | Saved for reading without a connection. | frakoblet | |
| follow | 12 | Following a post or user for updates. | følg | |
| Popular | 11 | The Popular feed. | Populær | |
| ban | 10 | A moderator banning a user from a community. | utesteng | |
| Direct Chat | 10 | Reddit's chat as a feature name. | Direkte Chat | |
| Joined | 9 | The tab and list of communities the reader is a member of. | Medlem | |
| leave | 9 | Leaving (unsubscribing from) a community. | forlat | |
| mod queue | 9 | Moderators' list of items to review. | modkø | |
| sort | 9 | Sort order of posts or comments. | sorter | |
| Unread | 9 | Not yet read. | ules | |
| approve | 8 | A moderator approving an item. | godkjenn | |
| distinguish | 8 | A moderator marking their comment as a moderator's. | merk som moderator, moderatormerke | |
| notification | 7 | An Android notification. | varsel | |
| removal reason | 7 | A community's reason for removing an item. | fjerningsårsak | |
| pinned | 6 | Pinned to the top of a feed. | festet | |
| mod log | 6 | The list of moderator actions. | modlogg | |
| browser | 6 | The web browser. | nettleser | |
| online | 6 | Reddit's online status (whether others see you online). | nettstatus, pålogget | |
| scroll | 5 | Scrolling a list. | scroll | |
| User management | 5 | The moderator tool for managing users. | Brukeradministrasjon | |
| Your reading | 4 | The reading-history feature (a name). | Lesingen din | |

Names of screens, tabs and settings also appear inside sentences ("Turn on
Open supported links", "Show r/x in Joined"). Translate the name the same way
inside the sentence as on its own: the checker lists sentences where it differs.
