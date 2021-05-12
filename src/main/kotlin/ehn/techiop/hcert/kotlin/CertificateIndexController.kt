package ehn.techiop.hcert.kotlin

import ehn.techiop.hcert.kotlin.chain.CryptoService
import ehn.techiop.hcert.kotlin.chain.asBase64
import ehn.techiop.hcert.kotlin.chain.common.PkiUtils
import ehn.techiop.hcert.kotlin.chain.fromBase64
import ehn.techiop.hcert.kotlin.trust.TrustListV1EncodeService
import ehn.techiop.hcert.kotlin.trust.TrustListV2EncodeService
import eu.europa.ec.dgc.gateway.connector.DgcGatewayDownloadConnector
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64


@RestController
class CertificateIndexController(
    private val trustListServiceAdapter: TrustListServiceAdapter,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping(value = ["/cert/{kid}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getCertByKidText(@PathVariable kid: String): ResponseEntity<String> {
        log.info("/cert/$kid called (text)")
        return ResponseEntity.ok(loadCertificate(kid).asBase64())
    }

    @GetMapping(value = ["/cert/{kid}"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getCertByKidBinary(@PathVariable kid: String): ResponseEntity<ByteArray> {
        log.info("/cert/$kid called (binary)")
        return ResponseEntity.ok(loadCertificate(kid))
    }

    @GetMapping(value = ["/cert/list"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getTrustList(): ResponseEntity<ByteArray> {
        log.info("/cert/list called")
        return ResponseEntity.ok(trustListServiceAdapter.getTrustList())
    }

    @GetMapping(value = ["/cert/listv2"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getTrustListV2(): ResponseEntity<ByteArray> {
        log.info("/cert/listv2 called")
        return ResponseEntity.ok(trustListServiceAdapter.getTrustListV2Content())
    }

    @GetMapping(value = ["/cert/sigv2"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getTrustListSigV2(): ResponseEntity<ByteArray> {
        log.info("/cert/sigv2 called")
        return ResponseEntity.ok(trustListServiceAdapter.getTrustListV2Sig())
    }

    private fun loadCertificate(requestKid: String): ByteArray {
        val kid = Base64.getUrlDecoder().decode(requestKid)
        return trustListServiceAdapter.cryptoServices.map { it.getCertificate() }
            .firstOrNull { PkiUtils.calcKid(it) contentEquals kid }?.encoded
            ?: throw IllegalArgumentException("kid not known: $requestKid")
    }

}

class TrustListServiceAdapter(
    signingService: CryptoService,
    internal val cryptoServices: Set<CryptoService>,
    private val downloadConnector: DgcGatewayDownloadConnector
) {

    private val trustListService = TrustListV1EncodeService(signingService)
    private val trustListV2Service = TrustListV2EncodeService(signingService)
    private val internalCertificates = cryptoServices.map { it.getCertificate() }.toSet()
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    private fun loadGatewayCerts() =
        downloadConnector.trustedCertificates.map {
            certificateFactory.generateCertificate(it.rawData.fromBase64().inputStream()) as X509Certificate
        }.toSet()

    internal fun getTrustList() = trustListService.encode(internalCertificates + loadGatewayCerts())
    internal fun getTrustListV2Content() = trustListV2Service.encodeContent(internalCertificates + loadGatewayCerts())
    internal fun getTrustListV2Sig() = trustListV2Service.encodeSignature(getTrustListV2Content())

}
