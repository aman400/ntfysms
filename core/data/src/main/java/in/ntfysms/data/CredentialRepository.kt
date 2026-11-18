package `in`.ntfysms.data

import `in`.ntfysms.domain.model.Credential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CredentialRepository {
    fun getAllCredentialsFlow(): Flow<List<Credential>>
    suspend fun getCredentialById(id: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun deleteCredential(id: String)
}

class RoomCredentialRepository(
    private val credentialDao: CredentialDao
) : CredentialRepository {

    override fun getAllCredentialsFlow(): Flow<List<Credential>> {
        return credentialDao.getAllCredentialsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCredentialById(id: String): Credential? {
        return credentialDao.getCredentialById(id)?.toDomain()
    }

    override suspend fun saveCredential(credential: Credential) {
        val updated = credential.copy(updatedAt = System.currentTimeMillis())
        credentialDao.insertOrUpdateCredential(CredentialEntity.fromDomain(updated))
    }

    override suspend fun deleteCredential(id: String) {
        credentialDao.deleteCredentialById(id)
    }
}
