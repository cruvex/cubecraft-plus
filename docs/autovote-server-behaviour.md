# How CubeCraft's voting menus behave

Read from a hand-voting recording on 2026-11-05 (Minecraft 26.3, AutoVote off):
`run/cubecraft-plus/debug/signals-2026-11-05_23-49-01.log`. It covers 7 Solo SkyWars joins, 1 BedWars
lobby, 1 Team EggWars lobby and 20 votes of our own. The rebuild is written against this.

## Opening a menu

- **Every menu opens twice.** First a `generic_9x1` placeholder titled `Voting`, holding gray `Loading`
  panes (45 slots). Then the real `generic_9x4` (72 slots), whose title is custom-font glyphs + `Voting`.
  Submenus and `Team Selection` do the same.
- **No close packet between the two.** The second open simply replaces the first. Section 7 of
  `autovote-silent-plan.md` says every transition sends a close; that does not happen here.
- **Timings, hub menus included:**

| Step | Fastest | Typical | Slowest |
|---|---|---|---|
| Item use to placeholder (38 samples) | 24ms | 24-35ms (36 of 38) | 116ms |
| Placeholder to real menu (113) | 0ms | 0ms, 90% within 48ms | 378ms |
| `Back` click to placeholder (4) | 28ms | 30ms | 77ms |
| Vote click to foil (29) | 26ms | 31ms, 90% within 145ms | 363ms |

- The real menu's first contents packet is already complete. Up to two identical resends follow
  (stateId 2, 3), then the wallet item at slot 28 updates 15-50ms later, by slot or contents packet.
- Reopening right after closing works: the shortest recorded gap from close to use is 48ms, and four
  close-and-reopen cycles in about a second gave no trouble.

## Telling menus apart

Slot 31 says what kind of menu this is, through `cubetap:item_variant` in the item's custom data:

| Menu | Slot 31 | Variant |
|---|---|---|
| Top level (category list, BedWars' `Modifiers`, `Team Selection`) | `Close` | `close` |
| Category submenu | `Back` | `back` |

`Team Selection` has the same layout as a voting menu, so slot 31 alone is not enough: the title is
still needed to tell which menu it is. EggWars' titles are confirmed as `Perk Voting`, `Items Voting`
and `Health Voting`, and its slots match the config.

Vote options are identified by lore ending in `N votes`. Filler slots are `rabbit_foot` with variant
`dark_grey` and an empty name, and clicking one does nothing.

## A vote

- **Foil means "this is your vote".** The option you voted for gets foil; options with other players'
  votes do not. Foil is still there when the menu is reopened, including in its first contents packet.
- Changing your vote moves the foil and takes your votes off the old option.
- **Voting for your current option again does nothing.** Twelve repeat clicks, double clicks included,
  changed no votes and sent no chat line. The slot just came back unchanged, with its foil, in about
  30ms. There is no toggle and no sign of a rate limit.
- Item count and the lore line both show the vote total. One vote of ours is worth 3.
- The category list shows nothing about votes, so foil can only be read in the submenu.
- **The first update after a click is usually not the vote.** The server often reverts the clicked slot
  (no foil) and clears the cursor first. The foil follows through a contents packet, a slot packet or
  both, in either order.
- The chat line (`<name> voted for <option> <category>. N votes`) lands within about 100ms of the foil,
  before or after it. The name can carry rank decoration on both sides.
- **The server never closes the menu after a vote.** It stays open until we leave it.
- At game start the chat says `Selected Normal chest items.`, one line per category.

## Rate limit and closing time (from the step 3 tests, 2026-11-07)

- **Votes are rate limited.** A vote can be dropped with `You are voting too fast! Please slow down.`
  in chat; the clicked slot is reverted without foil. Seen after a hand vote in the same category
  1.2s and 2.1s earlier (three Chest Items votes in 3.5s), and on a hand vote too. Clicking again 1
  second later was accepted. Three votes in different EggWars categories within 2.3s were all
  accepted, so the limit looks per category, but that is not proven.
- **One click got no answer at all**: no revert, no foil, no chat line, no rate-limit message. It came
  3.3s after a hand vote in the same category and 81ms after `starting in 5 seconds`. A chest vote 38ms
  after that line was accepted in another lobby, so the cause is unknown.
- **SkyWars settles the projectile vote at `starting in 5 seconds`**: `Selected No throwables.` arrives
  with that line, while chests and time are only announced at game start. EggWars announces all three
  at game start.
- **A closed category says so.** Clicking Projectile Options in the category list after that line got
  `The votes for this category have been closed.` 24ms later, with the slot reverted and no submenu.
  Chests still took a vote at the 4-second mark, so each category closes on its own schedule.
- **The rate limit spans lobbies.** Hopping lobbies every few seconds, the third Chest Items vote in 12
  seconds (each in a different lobby) got `too fast` three times in a row; a run 2 seconds later went
  through. (2026-11-07, automatic runs.)
- **An item use too soon after connecting can be ignored.** In 4 of about 25 SkyWars joins, using the
  item 0.51s after connecting opened nothing, and using it again a second later worked. In one of them
  the server reset the held slot to 4 at +0.66s, as if it was still setting up the lobby. Uses from
  0.52s on usually worked, so the cut-off varies.
- **Using the item while a submenu is open nests the new list under it**: the category list it opens has
  `Back` in slot 31 instead of `Close`, though its title is still `Voting`. (Seen once, 2026-11-07.)

## Moving between menus

- `Back` (slot 31) is a plain click and opens the category list again, placeholder first. Recorded 4
  times, all of them working.
- Closing and using the item again also works, recorded many times, so it can serve as the retry path.

## What the client sends

- Item use: `USE_ON` main hand (if a block is targeted), `USE` main hand, `USE_ON` off hand.
- Click: `PICKUP`, button 0, `changed={slot=>empty}`, `carried` = the hashed stack, current count
  included. That is the shape `RemoteMenu.click` builds.
- The hotbar selection is sent on the tick before or with the use.

## Hotbar and game flow

- **SkyWars:** `Voting` is in slot 0 before `GAME_JOIN` fires. In 1 of 7 joins it vanished for a single
  tick at +340ms and came back. The earliest recorded use is 737ms after connecting, and it worked.
- **EggWars and BedWars:** slot 0 is `Team Selection` in the waiting lobby. `Voting` only replaces it
  1.3 seconds after `starting in 1 second`, once players are moved to the map, and the countdown then
  starts again. That leaves about 10 seconds: 9.8 in the recorded EggWars game. By hand, three
  categories took 5 seconds of that.
- A countdown can be short: one SkyWars lobby hit `starting in 5 seconds` 7.8 seconds after joining.
- **At game start the server closes an open voting menu**, using the menu's real id. The hotbar empties
  in the same moment. (Recorded once, in EggWars.)
- `ClientboundContainerClosePacket` with id 0 arrives on every server change and several times at game
  start. Vanilla closes whatever screen is open on it (it shut the chat in the BedWars lobby).
  It does not match a voting menu's id, so `RemoteMenu.closedByServer` ignores it.

## Not recorded yet

- Using the item in the first 700ms after joining.
- BedWars in a lobby that isn't full. EggWars behaved the same way as the full BedWars lobby.
- Any other game with a voting config.

## What this changes in the rebuild

1. Wait for the real menu (72 slots, slot 31 variant, title), never act on the placeholder. In silent
   mode the placeholder has to be hidden too.
2. A vote counts when the chosen slot shows foil, not on any redraw and not on a timer. Chat is logged
   as a second opinion.
3. If the chosen slot already has foil, skip the click. A repeat click is harmless, so retries are safe.
4. Check that a slot is a vote option (`N votes` lore) before clicking it, so a changed layout fails
   loudly instead of clicking a filler.
5. Walk categories with `Back`, as the old flow does. If a step fails, close and use the item again.
6. Keep each step's timeout near 1 second: the slowest recorded response was 378ms, and team games
   only give about 10 seconds to cast every vote.
7. Trigger on the hotbar item staying for a few ticks, not on `GAME_JOIN`. Team games have it much later.
8. Treat a close with id 0, or with the menu's own id, as "the menu is gone".
9. Accept the own-vote chat line as confirmation too: it can beat the foil, and it still arrives when the
   menu closed right after the click.
10. On the rate-limit message, click again after a second. On a click with no answer, retry once.
11. Vote as early as possible: in SkyWars the projectile vote is settled 5 seconds before the start.
12. On "votes for this category have been closed", skip that category at once instead of retrying, so
    the categories that are still open get their turn.
13. Recognise the category list by its whole plain title (`Voting`, glyphs stripped), not by slot 31,
    and close the open menu before using the item again.
14. Do not use the item in the first second after connecting.
15. A rate-limited category is worth one more run a couple of seconds later; that is what fixed it.
