---
name: create-style
description: Create a new style ( title, popup, backgrounds ...) for BRTS menus.
---

<!-- Tip: Use /create-skill in chat to generate content with agent assistance -->

Styles live in assets/styles. The entry point for a style is a JSON descriptor file (loaded as a org.brts.highlevel.style.StyleTemplateManifest), which references the other assets (images, fonts, etc.) that make up the style.
A working example is provided in assets/styles/sci-fi, which is used for some BRTS unit tests.