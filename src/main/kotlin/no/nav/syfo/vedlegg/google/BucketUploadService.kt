package no.nav.syfo.vedlegg.google

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.vedlegg.model.VedleggMessage
import java.util.UUID

class BucketUploadService(
    private val bucketName: String,
    private val storage: Storage
) {
    fun create(id: String, vedleggMessage: VedleggMessage) {
        storage.create(BlobInfo.newBuilder(bucketName, "$id/${UUID.randomUUID()}").build(), objectMapper.writeValueAsBytes(vedleggMessage))
        log.info("Lastet opp vedlegg med id $id for source ${vedleggMessage.source}")
    }
}
