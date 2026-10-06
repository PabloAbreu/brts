<#-- Fixed square corners and stretchable straight edges around a solid black center. -->
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="${width}" height="${height}" fill="#000000"/>
  <#if width gt 2 && height gt 2>
    <rect x="1" y="1" width="${width - 2}" height="${height - 2}" fill="none" stroke="#666666" stroke-width="2"/>
  </#if>
</svg>
