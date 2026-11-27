# Code and block editing

Edit:RiN keeps `sketch.js` as the executable source of truth. The block editor is
another view of that document. Opening or closing it must not rewrite the source.
Auxiliary JavaScript, HTML and CSS continue to use the code editor.

## Architecture

The Compose editor hosts a dedicated, offline Blockly WebView. Blockly handles
layout, connections and fields; it does not generate the complete sketch. Acorn
parses source in a worker, and the source adapter proposes range replacements.
The Kotlin session accepts a proposal only when its work, source and opaque document revision still match. The revision advances for source, metadata, history and editor activation changes; returning to identical source cannot revive an old operation.
The existing preview WebView executes the acknowledged sketch and owns recording,
screenshots, media permissions and runtime errors. The block WebView has none of
those permissions and never evaluates sketch source.

Dependencies are pinned to Blockly 13.4.0 and Acorn 8.19.0. Bundled asset provenance
and checksums live with the block assets; corresponding archives and license
texts are included under `third_party`. There is no CDN requirement.

The message channel is scoped to one WebView session. A `load` contains the work
ID, source/revision, optional block metadata, language, read-only state, runtime,
available asset names, theme and current errors. An `edit` contains a unique request ID, the expected document revision and
source, source patches and the next metadata string. The acknowledgement echoes
the request ID; an obsolete reply cannot finish a newer edit. Kotlin replies with `ack`
and the current authoritative source/metadata. Stale, malformed and read-only
edits are rejected. `openCode`, `openRuntime`, `undo` and `redo` request actions
from the native editor rather than exposing platform APIs to the page.

Before leaving the block view, saving, navigating or capturing a snapshot, native
code requests a flush. The editor waits for Blockly's animation-frame event
dispatch, pending worker operations and native acknowledgements. Flush requests
are serialized; rendering and source changes reject obsolete work. An unanswered
native flush offers recovery from acknowledged source instead of trapping the
user in a failed editor.
The frame-dispatch wait is bounded. If rendering is suspended, flush rejects
without consuming queued Blockly events; it can be retried when frames resume.
When draft recovery is enabled, the last validated input draft or native
acknowledged content is still captured after a failed flush, without reporting
the requested save as successful. The failure screen's code action explicitly detaches the failed
host before opening acknowledged native source.

Auto-draft flushes use `hideChaff: false`. Intermediate field events send
`draftChanged`, restarting the native draft delay even before source changes.
The `flushed` reply can carry a revision-scoped `draft` proposal with source
patches and metadata. Kotlin validates it for recovery storage only: it does not
change the executable document, acknowledge a content edit or add Undo history.
The input remains open for continued typing. An incomplete or stale proposal is
rejected, retaining the last validated recovery draft for the same document.
Cancelling the input clears that proposal; manual save/navigation commits the
field through the ordinary edit/ACK path.

## Source preservation

Ordinary p5 calls, expressions, declarations, functions and control structures
have editable projections. Other valid JavaScript stays in custom-code blocks.
Editing one field changes its source range; structural edits rebuild the smallest
affected statement list while retaining unchanged child source. A candidate is
parsed before it is proposed. No conversion promises to make arbitrary JavaScript
understandable as dedicated blocks.

Value replacements preserve expression grouping. Statement insertion, movement
and deletion protect both sides of the edit with AST-based terminators, including
object/function expressions, unbraced control flow and intervening comments.
Palette selection survives focus changes into search and location dialogs; name
choices use the lexical scope at the selected reference.

After a native ACK, immutable projection indexes resolve nodes by ID. Compatible
source edits retain existing visual blocks and their authoritative source ranges;
changed definitions replace only the affected blocks. The reconciler disconnects
obsolete graph edges before connecting new edges, avoiding implicit tail splices.
Changed detached-part sources or runtime environments fall back to a full rebuild.
A rejected ACK always restores the actual visual fields as well as the source.
Disposed block models and unused definitions are released after reconciliation.

Colour changes preserve alpha expressions and evaluate them once. Alpha-bearing
colours use a p5.Color with the current runtime's alpha scale, preserving RGB and
HSB/HSL behaviour. Further colour changes edit the contained hex value. Shadowed
runtime colour functions require code editing instead of an ambiguous rewrite.

Source comments, parameter annotations, parentheses, statement order, lexical
scope and zero-based array access remain JavaScript concepts. Blockly's standard
global variable and one-based list generators are not used. Moving the visual
position of a root block does not reorder execution.

When source is incomplete, the last valid projection is read-only and offers a
path back to the code location. Runtime-dependent APIs remain visible with their
requirements. Selecting a different runtime does not migrate existing code.

## Structural editing

Tapping a block opens a compact icon-only contextual pill (`Duplicate`, `Delete`,
and `More`, plus an error chip when that block has a runtime error). Color swatches,
value capsules and inline fields are edited directly on the block; `More` groups
call-argument/array-item/parameter edits, else-branch toggles, color/number/name
helpers, folding, code preview and code jumps in a single sheet.
Detached parts use the same operations against their own source and commit
metadata only. Confirmed changes remain a single native history operation. Adding a function parameter inserts a
zero argument at matching direct calls in `sketch.js`; removing an unused
parameter removes those matching arguments. Auxiliary JavaScript is outside this
refactoring boundary.

Renaming a declaration, or choosing a bulk rename, resolves lexical bindings
including uses inside custom code. It retains object keys, strings, comments and
shadowed names. Capture, duplicate definitions, dynamic scopes, imports/exports,
reflective global access and ambiguous parameter changes are rejected with an
explanation. Changing a reference field alone selects a different reference.

List edits retain separators and comments, including holes and trailing commas.
Else edits identify real parser tokens and protect dangling nested conditions;
a syntactically valid edit must retain which condition owns an else branch.

## Guided assets, events and 3D

`authoring.js` runs in the parser worker. It proposes ordinary source changes
for global resource declarations, loading, top-level callbacks and canvas
configuration. Image/audio selection uses native asset names and MIME types;
the existing preview owns actual loading and playback. The catalogue is the
shared registry for p5 names, events, runtime support and authoring actions.

Image and sound loading use `preload` in p5 1.x and an awaited `async setup` in
p5 2.x. Loaded resources remain accessible from draw and input handlers. Existing
custom callback definitions, shadowed runtime names and ambiguous canvases are
preserved with a code-editing explanation. Enabling p5.sound requires the native
runtime setting. Switching a canvas to WEBGL requires explicit confirmation;
changing p5 version does not migrate source. Legacy touch/preload additions are
available in 1.x; 2.x offers the supported mouse/input events.

Oscillators are initialized silently and started once following user input;
release fades their level instead of allocating another oscillator. Guided
p5.sound 2.x panning reuses a global panner, and custom audio routing stays in
code. The runtime browser suite executes these generated workflows in both
bundled p5 versions, including repeated trusted taps and rendered pixels.

## Authored state and recovery

The optional `Work.blockEditorState` is separate from project files. It records
block identity, arrangement and disconnected parts. It participates in work
copies, save/draft capture, snapshots, templates, conflict recovery and ZIP
transfer. It is excluded from execution and thumbnail inputs. An older app can
still read source, but re-saving there may drop block authoring information.

Source and metadata edits share the main document's history. A drag or field
gesture is one undoable operation. Pan/zoom and mode switching are view state.
The auxiliary-file history remains independent. A metadata-only change does not
trigger Auto-Run or a new thumbnail.

Unknown or damaged block metadata is retained while source remains editable.
Saving must retain draft/base-hash and external-conflict protection. A failed
save cannot discard newer source, metadata or history. After a worker or renderer
failure, recreate the editor from native acknowledged state.

## Validation

Run `node --test tests/blocks-core.test.cjs tests/blocks-authoring.test.cjs` for source-adapter and guided-authoring tests and
`node tests/blocks.browser.cjs` for the offline editor, worker and message channel.
The browser script uses the existing `BROWSER_BIN` convention. Run `npm test`
and `git diff --check` for existing regressions. Kotlin tests cover transaction,
history, serialization and recovery behavior and require the explicitly selected
Android test environment.

Android compilation and physical-device acceptance are separate evidence. Device
acceptance includes Japanese IME, TalkBack, rotation, gestures with 300–500 blocks,
preview coexistence, renderer recovery, sound activation by a user gesture, and
both bundled p5 runtimes. Headless browser tests cannot establish these behaviors.

## Workspace presentation

The block editor provides two switchable projection levels over the same `sketch.js`:
**Detailed** (the default on Android, persisted in `BlockEditorPresentationStore`) and
**Easy** (a simplified 1-line projection). Easy mode removes the outer `rin_program`
wrapper so top-level events (`setup`, `draw`, input handlers) sit as standalone cards
on the 2D canvas, flattens calls/declarations/count loops into 1-line blocks with
inline value capsules (slider, motion/position presets, variables, and validated
expressions), projects multi-statement idioms (`push`/`pop` isolation, radial
arrangement, 2D grid arrangement, motion-trail background, animated rainbow style)
as single blocks, and protects complex syntax as 1-line `Detailed code` blocks that
can jump into Detailed mode or the code editor.

The bottom floating dock exposes only `[+ Add] [Undo] [Redo] [More]`. The `More`
menu consolidates six block-specific actions: `Find in work`, `Easy / Detailed`
mode switching, `Split / Full screen`, `Fit to view`, `Code preview`, and `Display
settings`. Both modes share a 1-line block palette organized into six unified
categories (`Draw`, `Color & style`, `Layout & control`, `Vars & math`, `Events`,
and `Extensions`), omitting target-selector bars and raw JavaScript subtitle lines.

The full-screen control switches between an expanded editor with floating preview
and a split workspace. In full-screen block mode, the floating PiP preview sits
below the document header and owns preview playback/fullscreen actions so controls
are not duplicated in the top bar; `sketch.js` diagnostic banners above the canvas
and read-only status pills are also suppressed to avoid duplicate notifications.
The editor uses movable Compose content so the same host, message channel and
viewport survive layout changes. Both directions wait for a successful edit flush
before switching. Changing presentation is not a document edit; an unsuccessful
flush leaves the editor in place and renderer failure offers the existing
acknowledged-source recovery controls.

The Edit:RiN renderer extends the bundled Thrasos connection/layout engine with
compact corners, shallow connectors, subdued grid lines, and concise localized
captions without duplicate English suffixes. Role colours, field contrast and the
palette use the app's restrained visual styling. Initial zoom is 100 percent;
wide programs can be panned instead of being automatically reduced to fit.
Overview and manual zoom remain available. Browser checks cover presentation
messages and unchanged source/metadata, but native workspace sizing, keyboard,
rotation and WebView reparenting require Android device acceptance.

Standalone call captions and the statement grip move the containing statement,
while nested values keep their own value semantics. Unsupported edits restore the
acknowledged tree and leave an operation message visible until dismissed or a new
edit succeeds; clearing runtime errors must not clear an operation failure.

Detached roots use a dashed border and subdued opacity without adding an extra
status row. New parts are placed outside existing stack bounds. `Find in work`
unifies `All`, `Functions`, and `Detached` filters as tabs inside one search dialog,
separate from the palette's search for new templates. Results are capped at 100
with an explicit narrowing hint.

On Android, the file/mode bar and the block controls form one native Compose
header in both split and full-screen layouts. Add, Undo/Redo, work search, Full
screen and More use the app's native controls. The overflow menu, work search,
function/part locator and display settings use Material components, not embedded
web toolbars. The app's work-menu Search and Ctrl+F open the current mode's search
without switching a block document into code mode. Source-specific block fields,
palette and contextual actions remain attached to the Blockly canvas.

The host opts into native chrome in its load message; `chrome.js` hides the web
toolbar/status and publishes bounded view-only state. A revision-scoped command
channel invokes the same editing actions as the standalone page. Search results
are capped at 100 and accepted only for the latest request and current document
revision. Dismiss closes web overlays without changing source. Native controls
are disabled while a web overlay owns input; the full-screen escape is retained.
The offline page keeps its web controls when no native chrome is requested.

Code preview and overview live in More. Display settings offer text sizes from 12
to 22 and standard/roomy spacing. Theme changes update layout and hit geometry.
The private native presentation store remembers display preferences and each
work's viewport/selection across host recreation. View and preference messages
have strict native validation and never change source revisions, authoring
metadata, snapshots or Undo history. Save/navigation flushes send the latest view
before acknowledging completion.

Regression checks also cover reconnecting a detached statement stack into empty
and occupied bodies without losing its tail, disconnect-plus-coordinate events
removing the original source exactly once (including after reload and reconnect),
coordinate-only or absent move notifications reconciled against the actual
workspace forest, and displaced values distinguished from unsupported detaches
using their acknowledged source socket rather than nullable event parent IDs.
Only the validated source-change transaction may save source-bound roots as
detached parts; other metadata paths reject that intermediate state.
Checks also cover immediate flush failure restoring the acknowledged visual
tree, and releasing obsolete source-specific Blockly
definitions on rebuild. These checks use the real worker and message channel.
