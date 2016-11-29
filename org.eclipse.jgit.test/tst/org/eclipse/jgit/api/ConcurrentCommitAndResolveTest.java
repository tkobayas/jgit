/*
 * Copyright (C) 2016, Toshiya Kobayashi <toshiyakobayashi@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/**
 * Testing the git commit and resolve concurrently
 */
public class ConcurrentCommitAndResolveTest extends RepositoryTestCase {

	private static final int LOOP_NUM = 5000;

	@Test
	public void testCommitAndResolve()
			throws JGitInternalException, GitAPIException {

		try (final Git git = new Git(db)) {

			git.commit().setMessage("initial commit").call();

			AtomicBoolean finish = new AtomicBoolean(false);

			// committer thread
			Runnable committer = new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < LOOP_NUM; i++) {
						try {
							git.commit().setMessage("commit num " + i).call();
						} catch (GitAPIException e) {
							e.printStackTrace();
						}
					}
					finish.set(true);
				}
			};

			AtomicInteger failureCount = new AtomicInteger(0);

			// reader thread
			Runnable reader = new Runnable() {
				@Override
				public void run() {
					while (!finish.get()) {
						try {
							ObjectId objectId = git.getRepository()
									.resolve("master^{tree}");
							if (objectId == null) {
								// null is not acceptable
								failureCount.incrementAndGet();
							}
						} catch (Exception e) {
							// Exception is acceptable
						}
					}
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

			assertEquals(0, failureCount.intValue());

		}
	}
}
