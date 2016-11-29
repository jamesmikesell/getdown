package com.threerings.getdown.data;

import static com.threerings.getdown.Log.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.samskivert.io.StreamUtil;
import com.samskivert.util.StringUtil;
import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.ProgressObserver;

/**
 * Models a single file resource used by an {@link Application}.
 */
public class JvmResource implements Resource
{

	private String _path;
	private URL _remote;
	private File _local;
	private File _marker;
	private File  _unpacked;
    private CompressionType _compressionType;
    private static final int DIGEST_BUFFER_SIZE = 5 * 1025;
    
    /**
     * Creates a resource with the supplied remote URL and local path.
     */
    public JvmResource (String path, URL remote, File local)
    {
    	log.info("jvm resource", path, remote, local);

    	_path = path;
        _remote = remote;
        _local = local;
        String lpath = _local.getPath();
        _marker = new File(lpath + "v");

        if(isJar(remote.getFile()))
        	_compressionType = CompressionType.Jar;
        if(isPacked200Jar(remote.getFile()))
        	_compressionType = CompressionType.Packed200Jar;
        if(isTarGz(remote.getFile()))
        	_compressionType = CompressionType.TarGz;
        if(isZip(remote.getFile()))
        	_compressionType = CompressionType.Zip;

    	log.info("file type", _compressionType);

		if (_compressionType == CompressionType.TarGz)
		{
			_unpacked = _local.getParentFile();
		}
		else if (_compressionType == CompressionType.Zip)
		{
			_unpacked = _local.getParentFile();
		}
		else if (_compressionType == CompressionType.Jar)
		{
			_unpacked = _local.getParentFile();
		}
		else if (_compressionType == CompressionType.Packed200Jar)
		{
			String dotJar = ".jar", lname = _local.getName();
			String uname = lname.substring(0, lname.lastIndexOf(dotJar) + dotJar.length());
			_unpacked = new File(_local.getParent(), uname);
		}
    }

    /**
     * Returns the path associated with this resource.
     */
    public String getPath ()
    {
        return _path;
    }

    /**
     * Returns the local location of this resource.
     */
    public File getLocal ()
    {
        return _local;
    }

    /**
     *  Returns the final target of this resource, whether it has been unpacked or not.
     */
    public File getFinalTarget ()
    {
        return shouldUnpack() ? _unpacked : getLocal();
    }

    /**
     * Returns the remote location of this resource.
     */
    public URL getRemote ()
    {
        return _remote;
    }

    /**
     * Returns true if this resource should be unpacked as a part of the
     * validation process.
     */
    public boolean shouldUnpack ()
    {
        return true;
    }

    /**
     * Computes the MD5 hash of this resource's underlying file.
     * <em>Note:</em> This is both CPU and I/O intensive.
     */
    public String computeDigest (MessageDigest md, ProgressObserver obs)
        throws IOException
    {
        return computeDigest(_local, md, obs);
    }

    /**
     * Returns true if this resource has an associated "validated" marker
     * file.
     */
    public boolean isMarkedValid ()
    {
        if (!_local.exists()) {
            clearMarker();
            return false;
        }
        return _marker.exists();
    }

    /**
     * Creates a "validated" marker file for this resource to indicate
     * that its MD5 hash has been computed and compared with the value in
     * the digest file.
     *
     * @throws IOException if we fail to create the marker file.
     */
    public void markAsValid ()
        throws IOException
    {
        _marker.createNewFile();
    }

    /**
     * Removes any "validated" marker file associated with this resource.
     */
    public void clearMarker ()
    {
        if (_marker.exists()) {
            if (!_marker.delete()) {
                log.warning("Failed to erase marker file '" + _marker + "'.");
            }
        }
    }

    /**
     * Unpacks this resource file into the directory that contains it. Returns
     * false if an error occurs while unpacking it.
     */
    public boolean unpack ()
    {
        // sanity check
        if (_compressionType == null) {
            log.warning("Requested to unpack non-jar file '" + _local + "'.");
            return false;
        }
        try {
            if (_compressionType == CompressionType.Jar) 
                return FileUtil.unpackJar(new JarFile(_local), _unpacked);
            if (_compressionType == CompressionType.Packed200Jar) 
                return FileUtil.unpackPacked200Jar(_local, _unpacked);
			if (_compressionType == CompressionType.Zip)
				return FileUtil.unpackJvm(_local, _unpacked, _compressionType);
			if (_compressionType == CompressionType.TarGz)
				return FileUtil.unpackJvm(_local, _unpacked, _compressionType);

            log.warning("Unknown compression type '" + _local);
            return false;
        } catch (IOException ioe) {
            log.warning("Failed to create JarFile from '" + _local + "': " + ioe);
            return false;
        }
    }

    @Override public boolean equals (Object other)
    {
        if (other instanceof JvmResource) {
            return _path.equals(((JvmResource)other)._path);
        } else {
            return false;
        }
    }

    @Override public int hashCode ()
    {
        return _path.hashCode();
    }

    @Override public String toString ()
    {
        return _path;
    }

    /**
     * Computes the MD5 hash of the supplied file.
     */
    public static String computeDigest (File target, MessageDigest md, ProgressObserver obs)
        throws IOException
    {
        md.reset();
        byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        int read;

        boolean isJar = isJar(target.getPath());
        boolean isPacked200Jar = isPacked200Jar(target.getPath());

        // if this is a jar, we need to compute the digest in a "timestamp and file order" agnostic
        // manner to properly correlate jardiff patched jars with their unpatched originals
        if (isJar || isPacked200Jar){
            File tmpJarFile = null;
            JarFile jar = null;
            try {
                // if this is a compressed jar file, uncompress it to compute the jar file digest
                if (isPacked200Jar){
                    tmpJarFile = new File(target.getPath() + ".tmp");
                    FileUtil.unpackPacked200Jar(target, tmpJarFile);
                    jar = new JarFile(tmpJarFile);
                } else{
                    jar = new JarFile(target);
                }

                List<JarEntry> entries = Collections.list(jar.entries());
                Collections.sort(entries, ENTRY_COMP);

                int eidx = 0;
                for (JarEntry entry : entries) {
                    // skip metadata; we just want the goods
                    if (entry.getName().startsWith("META-INF")) {
                        updateProgress(obs, eidx, entries.size());
                        continue;
                    }

                    // add this file's data to the MD5 hash
                    InputStream in = null;
                    try {
                        in = jar.getInputStream(entry);
                        while ((read = in.read(buffer)) != -1) {
                            md.update(buffer, 0, read);
                        }
                    } finally {
                        StreamUtil.close(in);
                    }
                    updateProgress(obs, eidx, entries.size());
                }

            } finally {
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (IOException ioe) {
                        log.warning("Error closing jar", "path", target, "jar", jar, "error", ioe);
                    }
                }
                if (tmpJarFile != null) {
                    tmpJarFile.delete();
                }
            }

        } else {
            long totalSize = target.length(), position = 0L;
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(target);
                while ((read = fin.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                    position += read;
                    updateProgress(obs, position, totalSize);
                }
            } finally {
                StreamUtil.close(fin);
            }
        }
        return StringUtil.hexlate(md.digest());
    }

    /** Helper function to simplify the process of reporting progress. */
    protected static void updateProgress (ProgressObserver obs, long pos, long total)
    {
        if (obs != null) {
            obs.progress((int)(100 * pos / total));
        }
    }

    protected static boolean isJar (String path)
    {
        return path.endsWith(".jar");
    }

    protected static boolean isTarGz (String path)
    {
        return path.endsWith(".tar.gz");
    }

    protected static boolean isZip (String path)
    {
        return path.endsWith(".zip");
    }

    protected static boolean isPacked200Jar (String path)
    {
        return path.endsWith(".jar.pack") || path.endsWith(".jar.pack.gz");
    }

    	
    /** Used to sort the entries in a jar file. */
    protected static final Comparator<JarEntry> ENTRY_COMP = new Comparator<JarEntry>() {
        public int compare (JarEntry e1, JarEntry e2) {
            return e1.getName().compareTo(e2.getName());
        }
    };

}
