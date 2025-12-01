# Recipe Overlay System - Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MINECRAFT CLIENT                             │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                    Player Opens Machine                      │   │
│  └─────────────────────┬────────────────────────────────────────┘   │
│                        │                                             │
│                        ▼                                             │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │          MachineAutomationHandler                           │   │
│  │  - onContainerOpen(title)                                   │   │
│  │  - getCurrentMachine() → SlimefunMachineData               │   │
│  └─────────────────────┬────────────────────────────────────────┘   │
│                        │                                             │
│                        ▼                                             │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │           RecipeOverlayRenderer (MAIN)                      │   │
│  │  ┌─────────────────────────────────────────────────┐       │   │
│  │  │ initialize()                                     │       │   │
│  │  │  ├─ Load recipe_overlay_config.json            │       │   │
│  │  │  └─ Cache config values                         │       │   │
│  │  └─────────────────────────────────────────────────┘       │   │
│  │  ┌─────────────────────────────────────────────────┐       │   │
│  │  │ show(SlimefunMachineData)                       │       │   │
│  │  │  ├─ Set currentMachine                          │       │   │
│  │  │  ├─ Load recipes via RecipeDatabase             │       │   │
│  │  │  ├─ Reset selection index                       │       │   │
│  │  │  └─ Start fade-in animation                     │       │   │
│  │  └─────────────────────────────────────────────────┘       │   │
│  │  ┌─────────────────────────────────────────────────┐       │   │
│  │  │ render(GuiGraphics, partialTicks)              │       │   │
│  │  │  ├─ Calculate fade alpha                        │       │   │
│  │  │  ├─ Render background panel                     │       │   │
│  │  │  ├─ Render title text                          │       │   │
│  │  │  ├─ Render recipe list (with scroll)           │       │   │
│  │  │  │   └─ For each visible recipe:               │       │   │
│  │  │  │       ├─ Highlight if selected              │       │   │
│  │  │  │       ├─ Show recipe name & output          │       │   │
│  │  │  │       ├─ Show input count                   │       │   │
│  │  │  │       └─ Show completion percentage         │       │   │
│  │  │  └─ Render keybind hints                       │       │   │
│  │  └─────────────────────────────────────────────────┘       │   │
│  │  ┌─────────────────────────────────────────────────┐       │   │
│  │  │ Navigation Methods                              │       │   │
│  │  │  ├─ moveUp()                                    │       │   │
│  │  │  ├─ moveDown()                                  │       │   │
│  │  │  ├─ selectCurrent()                             │       │   │
│  │  │  └─ updateScrollOffset()                        │       │   │
│  │  └─────────────────────────────────────────────────┘       │   │
│  └────────────────────┬───────────────────────────────────────┘   │
│                       │                                            │
│           ┌───────────┴───────────┐                               │
│           │                       │                               │
│           ▼                       ▼                               │
│  ┌──────────────────┐   ┌──────────────────────┐                │
│  │  RecipeDatabase   │   │ RecipeOverlay        │                │
│  │                   │   │ InputHandler         │                │
│  │ initialize()      │   │                      │                │
│  │  └─ Load recipes  │   │ handleKeyPress()     │                │
│  │     from JSON     │   │  ├─ UP/DOWN          │                │
│  │                   │   │  ├─ ENTER            │                │
│  │ getRecipes        │   │  └─ ESC/R            │                │
│  │ ForMachine()      │   │                      │                │
│  │                   │   │ handleMouseScroll()  │                │
│  │ getCraftable      │   │                      │                │
│  │ Recipes()         │   │ handleMouseClick()   │                │
│  │                   │   │                      │                │
│  │ sortBy            │   │                      │                │
│  │ Completion()      │   │                      │                │
│  └──────┬────────────┘   └──────────┬───────────┘                │
│         │                           │                             │
│         ▼                           ▼                             │
│  ┌──────────────────┐   ┌──────────────────────┐                │
│  │   RecipeData     │   │   Player Actions      │                │
│  │                  │   │                       │                │
│  │ - recipeId       │   │ Press R → Toggle      │                │
│  │ - machineId      │   │ Press ↑↓ → Navigate  │                │
│  │ - inputs[]       │   │ Press Enter → Select  │                │
│  │ - outputs[]      │   │ Scroll → Navigate     │                │
│  │ - groupedInputs  │   │ Click → Select        │                │
│  │                  │   │                       │                │
│  │ RecipeOutput     │   └───────────────────────┘                │
│  │ - itemId         │                                             │
│  │ - displayName    │                                             │
│  │ - amount         │                                             │
│  └──────────────────┘                                             │
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │             Configuration & Data Sources                    │ │
│  │                                                             │ │
│  │  recipe_overlay_config.json                                │ │
│  │   ├─ Position & dimensions                                 │ │
│  │   ├─ Colors & theme                                        │ │
│  │   ├─ Animation settings                                    │ │
│  │   ├─ Display options                                       │ │
│  │   └─ Keybind mappings                                      │ │
│  │                                                             │ │
│  │  slimefun_recipes.json                                     │ │
│  │   ├─ Recipe definitions                                    │ │
│  │   ├─ Input requirements                                    │ │
│  │   └─ Output products                                       │ │
│  │                                                             │ │
│  │  en_us.json                                                │ │
│  │   └─ Overlay text translations                             │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌─────────────┐
│   Player    │
└──────┬──────┘
       │ Opens Machine GUI
       ▼
┌──────────────────────┐
│ MachineAutomation    │
│    Handler           │
└──────┬───────────────┘
       │ getCurrentMachine()
       ▼
┌──────────────────────┐     ┌─────────────────┐
│  Recipe Overlay      │────▶│  Recipe         │
│    Renderer          │     │  Database       │
└──────┬───────────────┘     └─────────────────┘
       │ show(machine)               │
       │                             │ getRecipesForMachine()
       │◀────────────────────────────┘
       │
       │ Load recipes
       ▼
┌──────────────────────┐
│  Recipe Data List    │
│  - Recipe 1          │
│  - Recipe 2          │
│  - Recipe 3          │
│  - ...               │
└──────┬───────────────┘
       │
       │ Render every frame
       ▼
┌──────────────────────┐
│   Overlay Display    │
│  ╔══════════════╗    │
│  ║ Machine Name ║    │
│  ╠══════════════╣    │
│  ║ > Recipe 1   ║◀── Selected
│  ║   Recipe 2   ║    │
│  ║   Recipe 3   ║    │
│  ╚══════════════╝    │
└──────┬───────────────┘
       │
       │ Player Input
       ▼
┌──────────────────────┐
│  Input Handler       │
│  - Keyboard          │
│  - Mouse             │
└──────┬───────────────┘
       │
       │ Navigate / Select
       ▼
┌──────────────────────┐
│  Update Selection    │
│  or Select Recipe    │
└──────┬───────────────┘
       │ selectCurrent()
       ▼
┌──────────────────────┐
│ MachineAutomation    │
│ Handler.setSelected  │
│ Recipe(recipeId)     │
└──────────────────────┘
```

## Component Interaction

```
┌─────────────────────────────────────────────────────────────┐
│                    RENDER THREAD                             │
│                                                              │
│   Every Frame:                                               │
│   ┌────────────────────────────────────────────┐           │
│   │ RecipeOverlayRenderer.render()             │           │
│   │   if (overlayVisible)                      │           │
│   │      calculate alpha (fade animation)      │           │
│   │      draw background                       │           │
│   │      draw title                           │           │
│   │      for each visible recipe:             │           │
│   │         draw recipe entry                  │           │
│   │         highlight if selected              │           │
│   │      draw keybind hints                   │           │
│   └────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    INPUT THREAD                              │
│                                                              │
│   On Key Press:                                              │
│   ┌────────────────────────────────────────────┐           │
│   │ RecipeOverlayInputHandler.handleKeyPress() │           │
│   │   if (overlay visible)                     │           │
│   │      switch (key)                          │           │
│   │         UP → moveUp()                      │           │
│   │         DOWN → moveDown()                  │           │
│   │         ENTER → selectCurrent()            │           │
│   │         ESC → hide()                       │           │
│   └────────────────────────────────────────────┘           │
│                                                              │
│   On Mouse Event:                                            │
│   ┌────────────────────────────────────────────┐           │
│   │ RecipeOverlayInputHandler.handleMouse...() │           │
│   │   calculate mouse position                 │           │
│   │   check if in overlay bounds               │           │
│   │   scroll → navigate                        │           │
│   │   click → select recipe                    │           │
│   └────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   INITIALIZATION                             │
│                                                              │
│   Mod Load:                                                  │
│   RecipeDatabase.initialize()                               │
│      └─ Parse slimefun_recipes.json                         │
│      └─ Build recipe index                                  │
│                                                              │
│   RecipeOverlayRenderer.initialize()                        │
│      └─ Parse recipe_overlay_config.json                    │
│      └─ Cache config values                                 │
│      └─ Prepare rendering data                              │
└─────────────────────────────────────────────────────────────┘
```

## State Machine

```
┌─────────────┐
│   HIDDEN    │◀──────────┐
└──────┬──────┘           │
       │ toggle()         │ hide()
       │ (R key)          │ (ESC key)
       ▼                  │
┌─────────────┐           │
│   SHOWING   │           │
│  (fade in)  │           │
└──────┬──────┘           │
       │ fade complete    │
       ▼                  │
┌─────────────┐           │
│   VISIBLE   │───────────┘
│ (navigable) │
└──────┬──────┘
       │ selectCurrent()
       │ (Enter key)
       ▼
┌─────────────┐
│  SELECTING  │
│ (send to    │
│  handler)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   HIDDEN    │
└─────────────┘
```

## File Dependencies

```
RecipeOverlayRenderer.java
  │
  ├── depends on → RecipeData.java
  │                  └── depends on → RecipeHandler.RecipeIngredient
  │
  ├── depends on → RecipeDatabase.java
  │                  ├── depends on → RecipeData.java
  │                  └── reads → slimefun_recipes.json
  │
  ├── reads → recipe_overlay_config.json
  │
  └── integrates with → MachineAutomationHandler.java
                           └── depends on → SlimefunMachineData.java

RecipeOverlayInputHandler.java
  │
  └── controls → RecipeOverlayRenderer.java

recipe_overlay_config.json
  │
  └── configures → RecipeOverlayRenderer.java

en_us.json
  │
  └── provides text for → RecipeOverlayRenderer.java
```
