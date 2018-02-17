<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:kml="http://www.opengis.net/kml/2.2">
    <xsl:strip-space elements="*"/>

    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="kml:color/text()">
        <xsl:choose>
            <xsl:when test="../../../../kml:description/text() = 'Green'">ff00ff00</xsl:when>
            <xsl:when test="../../../../kml:description/text() = 'Red'">ff0000ff</xsl:when>
            <xsl:when test="../../../../kml:description/text() = 'Blue'">ffff0000</xsl:when>
            <xsl:when test="../../../../kml:description/text() = 'Black'">ff000000</xsl:when>
            <xsl:otherwise>ff888888</xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>

