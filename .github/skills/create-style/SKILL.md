---
name: create-style
description: Create a new style ( title, popup, backgrounds ...) for BRTS menus.
---

<!-- Tip: Use /create-skill in chat to generate content with agent assistance -->

Styles live in assets/styles. The entry point for a style is a JSON descriptor file (loaded as a org.brts.highlevel.style.StyleTemplateManifest), which references the other assets (images, fonts, etc.) that make up the style.
A working example is provided in assets/styles/sci-fi, which is used for some BRTS unit tests.
SVGs files should be in fact freemarker templates (.ftl) to allow dynamic content generation.
In particular width, height are expected to be dynamic. The existing SVG templates in assets/styles/sci-fi demonstrate this approach.
Each rectangular SVG can de conceptually divided in 9 zones : 
- 4 corners (top-left, top-right, bottom-left, bottom-right)
- 4 edges (top, bottom, left, right)
- 1 center
This allows for scalable designs where corners remain fixed, edges stretch in one direction, and the center stretches in both directions.