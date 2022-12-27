package com.genonbeta.android.framework.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.*
import java.net.URLConnection
import java.util.*
import kotlin.math.ln
import kotlin.math.pow

object LeafFile {

    fun getDocumentFilePublicDirectory(name: String): DocumentFile {
        return DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory(name))
    }

    @JvmStatic
    fun getDocumentFile(context: Context, uri: Uri): DocumentFile {
        return DocumentFile.fromUri(context, uri)
    }



    @Throws(IOException::class)
    fun createFileWithNestedPaths(
        context: Context,
        directory: DocumentFile,
        path: String?,
        mimeType: String,
        fileName: String,
        createIfNeeded: Boolean = true,
    ): DocumentFile {
        val documentFile = if (path == null) directory else makeDirs(context, directory, path, createIfNeeded)
        val result = documentFile.findFile(context, fileName) ?: if (createIfNeeded) {
            documentFile.createFile(context, mimeType, fileName) ?: throw IOException(
                "Could not create the file: $fileName"
            )
        } else {
            throw IOException("The lookup-only file '$fileName' does not exist")
        }

        if (!result.isFile()) {
            throw IOException("Found a different type of content where the file should be")
        }

        return result
    }

    @Throws(IOException::class)
    fun makeDirs(
        context: Context,
        directory: DocumentFile,
        path: String,
        createIfNeeded: Boolean = true,
    ): DocumentFile {
        var current = directory
        val pathArray = path.split(File.separator.toRegex()).toTypedArray()

        for (currentPath in pathArray) {
            current = current.findFile(context, currentPath) ?: if (createIfNeeded) {
                current.createDirectory(context, currentPath) ?: throw IOException("Failed to create dirs: $path")
            } else {
                throw IOException("Could not find the folder: $path")
            }

            if (!current.isDirectory()) {
                throw IOException("Found a non-directory where the directory should be")
            }
        }

        return current
    }

    fun formatLength(length: Long, kilo: Boolean = false): String {
        val unit = if (kilo) 1000 else 1024
        if (length < unit) return "$length B"
        val expression = (ln(length.toDouble()) / ln(unit.toDouble())).toInt()
        val prefix = (if (kilo) "kMGTPE" else "KMGTPE")[expression - 1] + if (kilo) "" else "i"
        return String.format(
            Locale.getDefault(), "%.1f %sB", length / unit.toDouble().pow(expression.toDouble()), prefix
        )
    }

    fun getFileContentType(fileUrl: String): String {
        val nameMap = URLConnection.getFileNameMap()
        val fileType = nameMap.getContentTypeFor(fileUrl)
        return fileType ?: "*/*"
    }

    @JvmStatic
    fun getFileExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')

        if (dotIndex >= 0) {
            val extension = fileName.substring(dotIndex + 1).lowercase(Locale.ROOT)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) return ".$extension"
        }

        return ""
    }

    @JvmStatic
    fun getUniqueFileName(
        context: Context,
        directory: DocumentFile,
        fileName: String,
    ): String {
        if (directory.findFile(context, fileName) == null)
            return fileName

        val pathStartPosition = fileName.lastIndexOf(".")
        var mergedName = if (pathStartPosition != -1) fileName.substring(0, pathStartPosition) else fileName
        var fileExtension = if (pathStartPosition != -1) fileName.substring(pathStartPosition) else ""
        if (mergedName.isEmpty() && fileExtension.isNotEmpty()) {
            mergedName = fileExtension
            fileExtension = ""
        }
        for (exceed in 1..998) {
            val newName = "$mergedName ($exceed)$fileExtension"
            if (directory.findFile(context, newName) == null) return newName
        }
        return fileName
    }

    fun uriExists(context: Context, uri: Uri, isMounted: Boolean = false): Boolean {
        try {
            return DocumentFile.fromUri(context, uri, isMounted).exists()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    fun schemeFile(uri: Uri): Boolean {
        try {
            val s = uri.scheme
            return s.equals(ContentResolver.SCHEME_FILE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun schemeContent(uri: Uri): Boolean {
        try {
            val s = uri.scheme
            return s.equals(ContentResolver.SCHEME_CONTENT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun getStandardPath(uri : Uri): Uri {
        try {
            if (schemeFile(uri)) {
                return uri
            } else {
                val primary = "primary"
                val uriPath = uri.path!! // e.g. /tree/primary:Music
                val storageId = uriPath.substringBefore(':').substringAfterLast('/')

                val rootFolder = uriPath.substringAfter(':', "")
                val getPath = rootFolder.substringAfter(':', "")
                val newUri = if (storageId == primary) {
                    DocumentFile.fromFile(File("/storage/emulated/0/$getPath")).getUri()
                } else {
                    DocumentFile.fromFile(File("/storage/$storageId/$getPath")).getUri()
                }

                return newUri
            }
        } catch (e: Exception) {
            return uri
        }
    }

    fun availableStorage(): List<DocumentFile> {
        val path = ArrayList<DocumentFile>()

        for (storage in getMediaDirectories()) {
            val file = File(storage)
            if (file.exists())
                path.add(DocumentFile.fromFile(file))
        }

        return path
    }

    fun getMediaDirectories() = mutableListOf<String>().apply {
        add(EXTERNAL_PUBLIC_DIRECTORY)
        addAll(externalStorageDirectories)
    }

    val EXTERNAL_PUBLIC_DIRECTORY: String = Environment.getExternalStorageDirectory().path
    //Devices mountpoints management
    private val typeWL = listOf("vfat", "exfat", "sdcardfs", "fuse", "ntfs", "fat32", "ext3", "ext4", "esdfs")
    private val typeBL = listOf("tmpfs")
    private val mountWL = arrayOf("/mnt", "/Removable", "/storage")
    val mountBL = arrayOf(EXTERNAL_PUBLIC_DIRECTORY, "/mnt/secure", "/mnt/shell", "/mnt/asec", "/mnt/nand", "/mnt/runtime", "/mnt/obb", "/mnt/media_rw/extSdCard", "/mnt/media_rw/sdcard", "/storage/emulated", "/var/run/arc")
    private val deviceWL = arrayOf("/dev/block/vold", "/dev/fuse", "/mnt/media_rw", "passthrough", "//")

    // skip if already in list or if type/mountpoint is blacklisted
    // check that device is in whitelist, and either type or mountpoint is in a whitelist
    val externalStorageDirectories: List<String>
        get() {
            var bufReader: BufferedReader? = null
            val list = ArrayList<String>()
            try {
                bufReader = BufferedReader(FileReader("/proc/mounts"))
                var line = bufReader.readLine()
                while (line != null) {

                    val tokens = StringTokenizer(line, " ")
                    val device = tokens.nextToken()
                    val mountpoint = tokens.nextToken().replace("\\\\040".toRegex(), " ")
                    val type = if (tokens.hasMoreTokens()) tokens.nextToken() else null
                    if (list.contains(mountpoint) || typeBL.contains(type) || startsWith(mountBL, mountpoint)) {
                        line = bufReader.readLine()
                        continue
                    }
                    if (startsWith(deviceWL, device) && (typeWL.contains(type) || startsWith(mountWL, mountpoint))) {
                        val position = containsName(list, mountpoint.getFileNameFromPath())
                        if (position > -1) list.removeAt(position)
                        list.add(mountpoint)
                    }
                    line = bufReader.readLine()
                }
            } catch (ignored: IOException) {
            } finally {
                close(bufReader)
            }
            list.remove(EXTERNAL_PUBLIC_DIRECTORY)
            return list
        }

    //TODO: Remove this after convert the dependent code to kotlin
    fun startsWith(array: Array<String>, text: String) = array.any { text.startsWith(it)}
    //TODO: Remove this after convert the dependent code to kotlin
    fun containsName(list: List<String>, text: String) = list.indexOfLast { it.endsWith(text) }

    fun String.getFileNameFromPath() = substringBeforeLast('/')

    fun close(closeable: Closeable?): Boolean {
        if (closeable != null)
            try {
                closeable.close()
                return true
            } catch (e: IOException) {
            }

        return false
    }





    // SAF Utils
    fun findSafFile(context: Context, uri: Uri, treePref: String): DocumentFile? {
        uri.path?.let { path ->
            //val treePref = PreferenceUtil.safSdCardUri ?: return null
            val treeUri = Uri.parse(treePref)
            var documentFile = DocumentFile.fromUri(context, treeUri, true)
            val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in 3 until parts.size) {
                documentFile = documentFile.findFile(context, parts[i])!!
            }
            return documentFile
        }
        return null
    }

    fun isSAFAccessGranted(context: Context, safUri: String): Boolean {
        val perms = context.contentResolver.persistedUriPermissions
        for (perm in perms) {
            if (perm.uri.toString() == safUri && perm.isWritePermission) return true;
        }

        return false;
    }

    fun isSAFRequired(docFile: DocumentFile): Boolean {
        return !docFile.canWrite()
    }

    //TODO: need to fix
    fun isSAFRequired(context: Context, uri: Uri): Boolean {
        return isSAFRequired(DocumentFile.fromUri(context, uri))
    }

    fun isSAFRequiredInList(docs: List<DocumentFile>): Boolean {
        for (doc in docs) {
            if (isSAFRequired(doc)) return true
        }
        return false
    }
}