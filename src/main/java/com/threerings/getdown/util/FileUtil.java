//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.util;

import static com.threerings.getdown.Log.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;

import com.samskivert.io.StreamUtil;
import com.threerings.getdown.data.CompressionType;

/**
 * File related utilities.
 */
public class FileUtil extends com.samskivert.util.FileUtil
{
    /**
     * Gets the specified source file to the specified destination file by hook or crook. Windows
     * has all sorts of problems which we work around in this method.
     *
     * @return true if we managed to get the job done, false otherwise.
     */
    public static boolean renameTo (File source, File dest)
    {
        // if we're on a civilized operating system we may be able to simple rename it
        if (source.renameTo(dest)) {
            return true;
        }

        // fall back to trying to rename the old file out of the way, rename the new file into
        // place and then delete the old file
        if (dest.exists()) {
            File temp = new File(dest.getPath() + "_old");
            if (temp.exists()) {
                if (!temp.delete()) {
                    log.warning("Failed to delete old intermediate file " + temp + ".");
                    // the subsequent code will probably fail
                }
            }
            if (dest.renameTo(temp)) {
                if (source.renameTo(dest)) {
                    if (!temp.delete()) {
                        log.warning("Failed to delete intermediate file " + temp + ".");
                    }
                    return true;
                }
            }
        }

        // as a last resort, try copying the old data over the new
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(source);
            fout = new FileOutputStream(dest);
            StreamUtil.copy(fin, fout);
            if (!source.delete()) {
                log.warning("Failed to delete " + source +
                            " after brute force copy to " + dest + ".");
            }
            return true;

        } catch (IOException ioe) {
            log.warning("Failed to copy " + source + " to " + dest + ": " + ioe);
            return false;

        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }
    }

    /**
     * Reads the contents of the supplied input stream into a list of lines. Closes the reader on
     * successful or failed completion.
     */
    public static List<String> readLines (Reader in)
        throws IOException
    {
        List<String> lines = new ArrayList<String>();
        try {
            BufferedReader bin = new BufferedReader(in);
            for (String line = null; (line = bin.readLine()) != null; lines.add(line)) {}
        } finally {
            StreamUtil.close(in);
        }
        return lines;
    }

    /**
     * Unpacks a pack200 packed jar file from {@code packedJar} into {@code target}. If {@code
     * packedJar} has a {@code .gz} extension, it will be gunzipped first.
     */
    public static boolean unpackPacked200Jar (File packedJar, File target)
    {
        InputStream packedJarIn = null;
        FileOutputStream extractedJarFileOut = null;
        JarOutputStream jarOutputStream = null;
        try {
            extractedJarFileOut = new FileOutputStream(target);
            jarOutputStream = new JarOutputStream(extractedJarFileOut);
            packedJarIn = new FileInputStream(packedJar);
            if (packedJar.getName().endsWith(".gz")) {
                packedJarIn = new GZIPInputStream(packedJarIn);
            }
            Pack200.Unpacker unpacker = Pack200.newUnpacker();
            unpacker.unpack(packedJarIn, jarOutputStream);
            return true;

        } catch (IOException e) {
            log.warning("Failed to unpack packed 200 jar file", "jar", packedJar, "error", e);
            return false;

        } finally {
            StreamUtil.close(jarOutputStream);
            StreamUtil.close(extractedJarFileOut);
            StreamUtil.close(packedJarIn);
        }
    }

	public static boolean unpackJvm(File packedJar, File targetParent, CompressionType type)
	{
		log.info("Untaring jvm", packedJar, targetParent);

		File decompressionFolder = new File(targetParent, "temp_vm");
		File vmDir = new File(targetParent, LaunchUtil.LOCAL_JAVA_DIR);

		try
		{
			deleteRecursive(decompressionFolder);
			deleteRecursive(vmDir);
		}
		catch (Exception e)
		{
			log.warning("Error deleting old vm folder", e);
			return false;
		}

		decompressionFolder.mkdirs();
		vmDir.mkdirs();

		final UnArchiver ua;
		switch (type)
		{
		case TarGz:
			ua = new TarGZipUnArchiver();
			break;
		case Zip:
			ua = new ZipUnArchiver();
			break;
		default:
			log.warning("Error setting unarchiver", type);
			return false;
		}

		ua.setSourceFile(packedJar);
		ua.setOverwrite(true);
		ua.setDestDirectory(decompressionFolder);
		ua.extract();

		File binFolder = findFirstDirectory("bin", decompressionFolder);
		if (binFolder != null)
		{
			log.info("Bin found " + binFolder.getPath());
			try
			{
				for (File file : binFolder.getParentFile().listFiles())
				{
					File destination = new File(vmDir, file.getName());
					Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			catch (IOException e)
			{
				log.warning("Error copying files", e);
				return false;
			}

			try
			{
				deleteRecursive(decompressionFolder);
			}
			catch (Exception e)
			{
				log.warning("Error deleting compression folder", e);
			}

			return true;
		}

		return false;

	}
    
	private static File findFirstDirectory(String name, File directory)
	{
		if (directory.isDirectory())
		{
			if (name.toLowerCase().equals(directory.getName().toLowerCase()))
				return directory;

			for (File file : directory.listFiles())
			{
				File found = findFirstDirectory(name, file);
				if (found != null)
					return found;
			}
		}

		return null;
	}

	private static void deleteRecursive(File dir)
	{
		File[] currList;
		Stack<File> stack = new Stack<File>();
		stack.push(dir);
		while (!stack.isEmpty())
		{
			if (stack.lastElement().isDirectory())
			{
				currList = stack.lastElement().listFiles();
				if (currList.length > 0)
				{
					for (File curr : currList)
						stack.push(curr);
				}
				else
					stack.pop().delete();
			}
			else
				stack.pop().delete();
		}
	}
}
