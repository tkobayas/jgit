/*
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/**
 * Testing the git commit and resolve commands concurrently
 */
public class ConcurrentCommitAndResolveTest extends RepositoryTestCase {
	@Test
	public void testSomeCommits()
			throws JGitInternalException, GitAPIException {

		final int committerCount = Integer
				.parseInt(System.getProperty("committer.count", "5"));
		final int readerCount = committerCount * 10;

		try (final Git git = new Git(db)) {

			git.commit().setMessage("initial commit").call();

			// committer thread
			Runnable committer = new Runnable() {
				@Override
				public void run() {
					long start = System.currentTimeMillis();
					System.out.println("committer start");
					try {
						for (int i = 0; i < committerCount; i++) {
							git.commit().setMessage("commit num " + i).call();
						}
					} catch (GitAPIException e) {
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
					try {
						for (int i = 0; i < readerCount; i++) {
							ObjectId objectId = git.getRepository()
									.resolve("master^{tree}");
							if (objectId == null) {
								System.out.println("objectId == null");
								failureSet.add(i);
							}
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("reader finished : elapsedTime = "
							+ (System.currentTimeMillis() - start) + " ms");
				}
			};
			ExecutorService executor = Executors.newFixedThreadPool(2);
			executor.execute(committer);
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
}
