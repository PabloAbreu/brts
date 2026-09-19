<#ftl output_format="XML">
<#-- FreeMarker SVG Template for End Credits (IMDb / TMDb cast & crew JSON format) -->
<#assign isAnimated = (animated!false)>
<#assign castList = (cast![])>
<#assign crewList = (crew![])>
<#assign lineHeight = 36>
<#assign sectionGap = 60>
<#assign currentY = 100>

<#-- Pre-calculate layout metrics -->
<#assign castHeaderY = currentY>
<#assign currentY = currentY + 50>
<#assign castStartY = currentY>
<#assign currentY = currentY + (castList?size * lineHeight) + sectionGap>

<#assign crewHeaderY = currentY>
<#assign currentY = currentY + 50>
<#assign crewStartY = currentY>
<#assign currentY = currentY + (crewList?size * lineHeight) + 120>
<#assign totalHeight = currentY>
<#assign scrollDuration = (totalHeight / 50)?string("0.0")>

<#-- Non-animated output is sized exactly to content; animated mode scrolls through a fixed viewport -->
<#assign viewportHeight = (viewportHeight!1080)>
<#assign svgHeight = isAnimated?then(viewportHeight, totalHeight)>

<svg xmlns="http://www.w3.org/2000/svg"
     xmlns:xlink="http://www.w3.org/1999/xlink"
     viewBox="0 0 1920 ${svgHeight}"
     width="1920"
     height="${svgHeight}">

  <defs>
    <linearGradient id="bgGrad" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#050811" stop-opacity="0.95"/>
      <stop offset="100%" stop-color="#0f1423" stop-opacity="0.95"/>
    </linearGradient>
    <filter id="textGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#000000" flood-opacity="0.8"/>
    </filter>
  </defs>

  <style>
    .bg { fill: url(#bgGrad); }
    .title { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 38px; font-weight: bold; fill: #f0f4f8; letter-spacing: 4px; text-anchor: middle; }
    .section-header { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 28px; font-weight: bold; fill: #d4af37; letter-spacing: 3px; text-anchor: middle; }
    .role { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 22px; fill: #94a3b8; text-anchor: end; }
    .name { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 22px; font-weight: 600; fill: #ffffff; text-anchor: start; }
    .separator { stroke: #334155; stroke-width: 1; }
  </style>

  <!-- Background -->
  <rect class="bg" width="100%" height="100%"/>

  <!-- Credits Container Group -->
  <g id="credits-content"<#if isAnimated> transform="translate(0, ${viewportHeight})"</#if>>
<#if isAnimated>
    <animateTransform attributeName="transform"
                      type="translate"
                      from="0 ${viewportHeight}"
                      to="0 -${totalHeight}"
                      dur="${scrollDuration}s"
                      repeatCount="indefinite"/>
</#if>

    <!-- Main Title -->
    <text x="960" y="50" class="title" filter="url(#textGlow)">CLOSING CREDITS</text>
    <line x1="860" y1="65" x2="1060" y2="65" class="separator"/>

<#if (castList?size > 0)>
    <!-- CAST Section -->
    <text x="960" y="${castHeaderY}" class="section-header">CAST</text>
    <line x1="900" y1="${castHeaderY + 12}" x2="1020" y2="${castHeaderY + 12}" class="separator"/>

  <#list castList as actor>
    <#assign actorY = castStartY + (actor?index * lineHeight)>
    <g class="credit-row">
      <text x="920" y="${actorY}" class="role">${actor.character!""}</text>
      <text x="1000" y="${actorY}" class="name">${actor.name!""}</text>
    </g>
  </#list>
</#if>

<#if (crewList?size > 0)>
    <!-- CREW Section -->
    <text x="960" y="${crewHeaderY}" class="section-header">CREW</text>
    <line x1="900" y1="${crewHeaderY + 12}" x2="1020" y2="${crewHeaderY + 12}" class="separator"/>

  <#list crewList as member>
    <#assign memberY = crewStartY + (member?index * lineHeight)>
    <#assign jobTitle = member.job!(member.department!"")>
    <g class="credit-row">
      <text x="920" y="${memberY}" class="role">${jobTitle}</text>
      <text x="1000" y="${memberY}" class="name">${member.name!""}</text>
    </g>
  </#list>
</#if>
  </g>
</svg>
