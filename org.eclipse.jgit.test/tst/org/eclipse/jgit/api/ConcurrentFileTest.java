package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ConcurrentFileTest {

	private static String TEST_FILE = "testfile";

	@Test
	public void testConcurrentSimpleFileAccess() throws Exception {

		final int writerCount = 5000;
		final int readerCount = writerCount * 10;

		File testFile = new File(TEST_FILE);
		FileWriter fw = new FileWriter(testFile);
		fw.write("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		fw.close();

		// committer thread
		Runnable writer = new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				System.out.println("writer start");
				try {
					for (int i = 0; i < writerCount; i++) {
						File testFile = new File(TEST_FILE);

						File lockFile = new File(TEST_FILE + ".lock");
						FileWriter fw = new FileWriter(lockFile);
						fw.write("BBBBBBBBBBBBBBBBBBBBBBBBBB" + i);
						fw.close();

						Files.move(lockFile.toPath(), testFile.toPath(),
								StandardCopyOption.ATOMIC_MOVE);

					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.println("committer finished : elapsedTime = "
						+ (System.currentTimeMillis() - start) + " ms");
			}
		};

		final Set<Integer> failureSet = new HashSet<Integer>();

		// reader thread
		Runnable reader = new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				System.out.println("reader start");

				for (int i = 0; i < readerCount; i++) {
					try {
						File testFile = new File(TEST_FILE);

						FileReader fr = new FileReader(testFile);
						while (fr.ready()) {
							fr.read();
						}
						fr.close();
					} catch (Exception e) {
						System.out.println(
								"reader error : " + e.getClass()
								+ " : " + e.getMessage());
					}
				}

				System.out.println("reader finished : elapsedTime = "
						+ (System.currentTimeMillis() - start) + " ms");
			}
		};
		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.execute(writer);
		executor.execute(reader);
		executor.shutdown();
		try {
			executor.awaitTermination(300, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertEquals(0, failureSet.size());

	}

}
