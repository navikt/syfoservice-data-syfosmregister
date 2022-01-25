package no.nav.syfo.vedlegg.google

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.syfo.log

class BucketService(
    private val bucketName: String,
    private val storage: Storage
) {
    fun create(id: String, byteArray: ByteArray, bucketName: String = this.bucketName) {
        storage.create(BlobInfo.newBuilder(bucketName, id).build(), byteArray)
        log.info("Lastet opp objekt med id $id")
    }

    fun getObjects(prefix: String, bucketName: String = this.bucketName): List<String> {
        return storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll().map {
            it.name
        }
    }
}
