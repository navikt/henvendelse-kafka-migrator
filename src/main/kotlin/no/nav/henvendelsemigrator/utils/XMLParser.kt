package no.nav.henvendelsemigrator.utils

import no.nav.henvendelsemigrator.domain.*
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class XMLParser {
    private val builder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
    private val xpath = XPathFactory.newDefaultInstance().newXPath()
    private val metadataPath = "/metadataListe/metadata"
    private val temagruppePath = "/metadataListe/metadata/temagruppe"
    private val fritekstPath = "/metadataListe/metadata/fritekst"
    private val kanalPath = "/metadataListe/metadata/kanal"
    private val navidentPath = "/metadataListe/metadata/navident"

    fun process(xml: String): Melding {
        val document = builder.parse(InputSource(StringReader(xml)))

        val meldingFraBruker: Boolean = evalNode(metadataPath, document)
            ?.attributes
            ?.getNamedItem("xsi:type")
            ?.nodeValue
            ?.removePrefix("ns2:")
            ?.equals("meldingFraBruker", ignoreCase = true)
            ?: false

        return if (meldingFraBruker) {
            MeldingFraBruker(
                temagruppe = evalNodeSet(temagruppePath, document)
                    ?.item(0)
                    ?.textContent
                    ?.let { Temagruppe.valueOf(it) },
                fritekst = evalNodeSet(fritekstPath, document)?.item(0)?.textContent
            )
        } else {
            MeldingTilBruker(
                temagruppe = evalNodeSet(temagruppePath, document)
                    ?.item(0)
                    ?.textContent
                    ?.let { Temagruppe.valueOf(it) },
                fritekst = evalNodeSet(fritekstPath, document)?.item(0)?.textContent,
                sporsmalsId = null,
                kanal = evalNodeSet(kanalPath, document)
                    ?.item(0)
                    ?.textContent
                    ?.let { Kanal.valueOf(it) },
                navident = evalNodeSet(navidentPath, document)?.item(0)?.textContent
            )
        }
    }

    private inline fun evalNodeSet(query: String, document: Document): NodeList? = xpath.evaluate(query, document, XPathConstants.NODESET) as NodeList?
    private inline fun evalNode(query: String, document: Document): Node? = xpath.evaluate(query, document, XPathConstants.NODE) as Node?
}
