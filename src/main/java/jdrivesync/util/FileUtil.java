package jdrivesync.util;

import jdrivesync.cli.Options;

import java.io.File;

public class FileUtil {

	private FileUtil() {

	}

	public static String toRelativePath(File file, Options options) {
		String absolutePathFile = file.getAbsolutePath();
		String absolutePathStartDir = options.getLocalRootDir().get().getAbsolutePath();
		String relativePath = "/";
		if (absolutePathFile.length() > absolutePathStartDir.length()) {
			relativePath = absolutePathFile.substring(absolutePathStartDir.length(), absolutePathFile.length());
			relativePath = relativePath.replace('\\', '/');
			if (!relativePath.startsWith("/")) {
				relativePath = "/" + relativePath;
			}
		}
		return relativePath;
	}
}
