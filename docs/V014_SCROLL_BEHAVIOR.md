# Android v0.14 Scroll Behavior

Source: physical tablet screen recording provided 2026-08-06.

- Main top app bar collapses while scrolling down.
- It reappears immediately when the user scrolls upward.
- The large Eyad PDF hero, search field and section heading scroll with the tool catalogue instead of permanently consuming vertical space.
- Navigation rail and bottom navigation remain available.
- Switching Tools and Files must not leave a long translucent overlap.
- No change to PDF engines or offline policy.

Acceptance: at least one card row must move beneath the former top-bar area while scrolling down, and reversing direction must restore the app bar without returning to the start of the list.
