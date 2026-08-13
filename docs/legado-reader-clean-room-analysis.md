# Legado Reader Reference Analysis (Clean-Room)

## License boundary

The uploaded Legado source is GPL-3.0. Vlalla currently does not declare a compatible license. Therefore this analysis treats Legado strictly as a product/behavior reference:

- no Legado Kotlin/Java/XML/assets are copied;
- no class hierarchy, method body, resource, icon, or wording is transplanted;
- vlalla improvements must be independently designed against vlalla’s Compose/Room architecture;
- if any future change intentionally derives from Legado source, the repository owner must first make an explicit licensing decision.

## Observed product concepts

Legado exposes a mature reading surface with:

1. explicit reading position at page/line/character granularity;
2. selectable page-turn strategies behind an abstraction;
3. nine-zone touch behavior, swipe, click paging, and long-press selection;
4. reader menu separated from the content surface;
5. font, line spacing, paragraph spacing, and page margin configuration;
6. chapter navigation, page progress, search, auto-page, TTS, and day/night controls;
7. persisted reader configuration and reading progress.

## Vlalla current state

Vlalla already has:

- Compose `HorizontalPager` chapter navigation;
- chapter boundary guards and explicit previous/next fallbacks;
- plain-text and dialogue reading modes;
- paragraph preservation;
- chapter-level progress in Room;
- TTS and excerpt-memory actions;
- serif prose and a fixed line-height visual contract.

Current gaps relevant to an overnight scope:

1. typography is fixed in `ReactReferenceContract`, not user-configurable;
2. reader mode is process-memory only and resets after process recreation;
3. progress is chapter-level only; reopening a long chapter starts at the top;
4. no independent, testable progress-percent/remaining-chapter policy;
5. menu combines modes/actions but has no dedicated appearance controls;
6. no auto-page or customizable tap zones (defer; too much interaction risk overnight).

## Clean-room implementation priorities

### Priority 1: Persisted reader preferences

Original vlalla design:

- introduce a `ReaderPreferences` value object with bounded font size, line-height multiplier, horizontal padding, and mode;
- persist it in a vlalla-owned `SharedPreferences`/DataStore schema;
- expose a flow from `AppViewModel`;
- render controls in the existing Compose bottom sheet;
- apply values only to vlalla’s existing text composables.

Acceptance tests:

- invalid values clamp to documented bounds;
- serialize/deserialize round-trip;
- plain/dialogue mode persists;
- default visual behavior remains unchanged.

### Priority 2: Reader progress policy and position restoration

Original vlalla design:

- create a serializable per-book/per-chapter position (`firstVisibleItemIndex`, `firstVisibleItemScrollOffset`) for plain/dialogue lists;
- debounce persistence to avoid database/write churn;
- restore only when the saved chapter ID matches current content;
- maintain chapter-level Room progress as the canonical cross-screen indicator.

Acceptance tests:

- wrong chapter IDs never restore stale offsets;
- negative/out-of-range offsets clamp safely;
- saved positions round-trip;
- changing chapters stops audio and preserves current existing behavior.

### Priority 3: Progress summary

Original vlalla design:

- pure `ReaderProgressPolicy` computes current chapter ordinal, percentage, and remaining chapters;
- show a single-line accessible status in the bottom bar;
- avoid estimating reading time without measured user speed.

Acceptance tests:

- empty book, first chapter, middle, and last chapter;
- no divide-by-zero;
- percentage is 0–100 and monotonic.

## Deferred backlog

- configurable tap zones;
- auto-page with pause/resume lifecycle;
- multiple page animation engines;
- in-book full-text search;
- per-book appearance profiles;
- line/character-granularity pagination;
- volume-key navigation.

These should be separate future projects because they affect gesture competition, selection, accessibility, lifecycle, and content layout.
