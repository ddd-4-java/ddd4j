# ddd4j Family Logo System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a browser-reviewable theme comparison and a deterministic six-project SVG/PNG logo family without overwriting existing brand assets.

**Architecture:** Keep one vector-first DDD/COLA mother geometry and generate six project variants by changing only the technology glyph, suffix, and approved theme tokens. The central comparison HTML embeds the same geometry and validates family resemblance, project distinction, light/dark rendering, grayscale behavior, and small-size legibility before assets are distributed to each repository.

**Tech Stack:** Static HTML/CSS/SVG, JavaScript-free visual comparison, deterministic SVG masters, browser screenshots, PNG exports.

**Spec:** `docs/superpowers/specs/2026-09-15-ddd4j-family-logo-system-design.md`

## Global Constraints

- Preserve all existing logos, covers, content images, README edits, and untracked user assets.
- Shared colors are `#10233F`, `#2457D6`, `#2AB7CA`, `#F7FAFF`, and `#D8E5F5`.
- Project defaults are ddd4j `#2457D6`, Boot `#5B8F3A`, Javalin `#E66A2C`, Quarkus `#A54078`, Web3 `#6A4DD8`, and Cloud `#1687A7`.
- Every icon uses one `0 0 512 512` mother geometry and one 30-degree isometric perspective.
- Each project receives new `-v2` files; no existing asset path is replaced.
- Icons must remain distinguishable at 128px, 48px, and 24px and inside a circular avatar crop.

---

### Task 1: Theme comparison HTML

**Files:**
- Create: `docs/branding/主题色比较稿.html`

**Interfaces:**
- Consumes: color tokens and semantic mappings from the approved spec.
- Produces: one self-contained HTML review artifact with inline SVG previews and no external dependencies.

- [x] **Step 1: Create the self-contained comparison page**

  Build a responsive static page containing the family rationale, shared color table, six project color cards, full-size icon prototypes, light/dark/gray previews, 128/48/24px tests, state-color separation, and cover/content color mapping.

- [x] **Step 2: Validate source contracts**

  Run:

  ```bash
  rg -n '#10233F|#2457D6|#2AB7CA|#5B8F3A|#E66A2C|#A54078|#6A4DD8|#1687A7' \
    docs/branding/主题色比较稿.html
  rg -n 'ddd4j-boot|ddd4j-javalin|ddd4j-quarkus|ddd4j-web3|ddd4j-cloud' \
    docs/branding/主题色比较稿.html
  ```

  Expected: every approved token and all six project names are present.

- [ ] **Step 3: Render and visually inspect desktop and mobile**

  Open the local HTML at 1280×1024 and 390×884. Verify no clipping, horizontal overflow, illegible text, broken SVG, or theme-color substitution.

- [ ] **Step 4: Check the task**

  Confirm the page clearly communicates one family before any project-specific symbol is read.

### Task 2: Deterministic vector masters

**Files:**
- Create: `tools/branding/generate-ddd4j-family.mjs`
- Create: `assets/branding/ddd4j-icon-v2.svg`
- Create: `assets/branding/ddd4j-logo-v2.svg`
- Create: five temporary central review pairs under `assets/branding/family-review/`

**Interfaces:**
- Consumes: one shared cube/COLA SVG template plus a project descriptor `{id, suffix, accent, dark, soft, glyph}`.
- Produces: standalone icon and horizontal lockup SVG files with exact project names and theme colors.

- [ ] **Step 1: Add generator contract checks**

  The generator must fail if a project lacks an exact name, accent, glyph, or output mapping; it must reject duplicate accent colors and any viewBox other than `0 0 512 512` for icons.

- [ ] **Step 2: Implement the shared mother geometry**

  Create three isometric cubes, the COLA layered aperture, one shared orbit, and fixed avatar safe area. Keep geometry identical across all six descriptors.

- [ ] **Step 3: Implement six glyphs**

  Add: Java steam plus keystone; Spring leaf plus start arc; Javalin sail plus request trail; Quarkus Q aperture plus native spark; Web3 coin, chain nodes, and lock; Cloud leaf, cloud arc, and service nodes.

- [ ] **Step 4: Generate review SVGs**

  Run:

  ```bash
  node tools/branding/generate-ddd4j-family.mjs
  ```

  Expected: twelve SVG files, six icons and six horizontal lockups.

- [ ] **Step 5: Validate SVG consistency**

  Parse every SVG as XML; compare the shared mother-geometry checksum; verify exact names, approved accent values, viewBoxes, and absence of embedded raster images.

### Task 3: Raster export and six-repository distribution

**Files:**
- Create: four `-v2` assets in each repository path listed by the spec.
- Create: `docs/branding/ddd4j-family-logo-gallery.png`

**Interfaces:**
- Consumes: approved standalone SVG masters from Task 2.
- Produces: transparent 1024×1024 icon PNGs, horizontal transparent logo PNGs, and a six-logo gallery.

- [ ] **Step 1: Export PNG variants**

  Render every icon at 1024×1024 with alpha and every lockup at a consistent horizontal size. Do not use image resampling from unrelated existing PNGs.

- [ ] **Step 2: Copy only new `-v2` files to target repositories**

  Preserve all existing files and dirty worktrees. Use the exact per-repository paths from the spec.

- [ ] **Step 3: Run visual gates**

  Produce a gallery at 128px, 48px, 24px, light, dark, and grayscale. Reject any icon whose technology glyph disappears or whose outer silhouette no longer resembles the family.

- [ ] **Step 4: Verify Git boundaries**

  For every repository, list the exact new files and prove no pre-existing file changed. Commit only files created by this plan after user approval of the gallery.
