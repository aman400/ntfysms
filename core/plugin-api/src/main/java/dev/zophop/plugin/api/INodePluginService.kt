package dev.zophop.plugin.api

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface INodePluginService : IInterface {
    fun getPluginApiVersion(): Int
    fun getManifestJson(): String
    fun getRequiredPermissions(): List<String>
    fun getSettingsIntentAction(): String
    fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String

    abstract class Stub : Binder(), INodePluginService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_getPluginApiVersion -> {
                    data.enforceInterface(DESCRIPTOR)
                    val res = getPluginApiVersion()
                    reply?.writeNoException()
                    reply?.writeInt(res)
                    true
                }
                TRANSACTION_getManifestJson -> {
                    data.enforceInterface(DESCRIPTOR)
                    val res = getManifestJson()
                    reply?.writeNoException()
                    reply?.writeString(res)
                    true
                }
                TRANSACTION_executeNode -> {
                    data.enforceInterface(DESCRIPTOR)
                    val nodeId = data.readString() ?: ""
                    val configJson = data.readString() ?: ""
                    val inputContextJson = data.readString() ?: ""
                    val res = executeNode(nodeId, configJson, inputContextJson)
                    reply?.writeNoException()
                    reply?.writeString(res)
                    true
                }
                TRANSACTION_getRequiredPermissions -> {
                    data.enforceInterface(DESCRIPTOR)
                    val res = getRequiredPermissions()
                    reply?.writeNoException()
                    reply?.writeStringList(res)
                    true
                }
                TRANSACTION_getSettingsIntentAction -> {
                    data.enforceInterface(DESCRIPTOR)
                    val res = getSettingsIntentAction()
                    reply?.writeNoException()
                    reply?.writeString(res)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        companion object {
            const val DESCRIPTOR = "dev.zophop.plugin.api.INodePluginService"
            const val TRANSACTION_getPluginApiVersion = FIRST_CALL_TRANSACTION + 0
            const val TRANSACTION_getManifestJson = FIRST_CALL_TRANSACTION + 1
            const val TRANSACTION_executeNode = FIRST_CALL_TRANSACTION + 2
            const val TRANSACTION_getRequiredPermissions = FIRST_CALL_TRANSACTION + 3
            const val TRANSACTION_getSettingsIntentAction = FIRST_CALL_TRANSACTION + 4

            fun asInterface(obj: IBinder?): INodePluginService {
                if (obj == null) return Proxy(null)
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is INodePluginService) return iin
                return Proxy(obj)
            }
        }

        private class Proxy(private val mRemote: IBinder?) : INodePluginService {
            override fun asBinder(): IBinder? = mRemote

            override fun getPluginApiVersion(): Int {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote?.transact(TRANSACTION_getPluginApiVersion, data, reply, 0)
                    reply?.readException()
                    reply?.readInt() ?: 1
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun getManifestJson(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote?.transact(TRANSACTION_getManifestJson, data, reply, 0)
                    reply?.readException()
                    reply?.readString() ?: ""
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(nodeId)
                    data.writeString(configJson)
                    data.writeString(inputContextJson)
                    mRemote?.transact(TRANSACTION_executeNode, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: ""
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun getRequiredPermissions(): List<String> {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote?.transact(TRANSACTION_getRequiredPermissions, data, reply, 0)
                    reply.readException()
                    val res = mutableListOf<String>()
                    reply.readStringList(res)
                    res
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun getSettingsIntentAction(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote?.transact(TRANSACTION_getSettingsIntentAction, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: ""
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
