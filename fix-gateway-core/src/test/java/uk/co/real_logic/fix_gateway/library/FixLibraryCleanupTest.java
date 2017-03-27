/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.library;

import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.DriverTimeoutException;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.junit.Test;
import uk.co.real_logic.fix_gateway.TestFixtures;

import java.io.File;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixLibraryCleanupTest
{
    @Test
    public void shouldCleanupLibrary()
    {
        final MediaDriver mediaDriver = TestFixtures.launchMediaDriver();
        try
        {
            final LibraryConfiguration configuration = new LibraryConfiguration()
                .libraryAeronChannels(singletonList(IPC_CHANNEL));
            final FixLibrary library = FixLibrary.connect(configuration);

            final File monitoringFile = new File(configuration.monitoringFile());
            final File histogramLoggingFile = new File(configuration.histogramLoggingFile());

            try
            {
                assertTrue(monitoringFile + " does not exist", monitoringFile.exists());
            }
            finally
            {
                CloseHelper.close(library);
            }

            assertFalse(monitoringFile + " hasn't been deleted", monitoringFile.exists());
            assertFalse(histogramLoggingFile + " hasn't been deleted", histogramLoggingFile.exists());
        }
        finally
        {
            TestFixtures.cleanupMediaDriver(mediaDriver);
        }
    }

    @Test
    public void shouldCleanupLibraryIfItCannotConnect()
    {
        final LibraryConfiguration configuration = new LibraryConfiguration()
            .libraryAeronChannels(singletonList(IPC_CHANNEL));

        shouldCleanupLibraryIfItCannotConnect(configuration);
    }

    private void shouldCleanupLibraryIfItCannotConnect(final LibraryConfiguration configuration)
    {
        configuration.aeronContext().driverTimeoutMs(50);

        try
        {
            FixLibrary.connect(configuration);
        }
        catch (final DriverTimeoutException e)
        {
            final File monitoringFile = new File(configuration.monitoringFile());
            final File histogramLoggingFile = new File(configuration.histogramLoggingFile());

            assertFalse(monitoringFile + " hasn't been deleted", monitoringFile.exists());
            assertFalse(histogramLoggingFile + " hasn't been deleted", histogramLoggingFile.exists());
        }
    }

    @Test
    public void shouldCleanupLibraryIfNoAeronCNCFile()
    {
        final LibraryConfiguration configuration = new LibraryConfiguration()
            .libraryAeronChannels(singletonList(IPC_CHANNEL));

        // Ensure that we test the CNC file not found path
        IoUtil.delete(new File(configuration.aeronContext().aeronDirectoryName()), true);

        shouldCleanupLibraryIfItCannotConnect(configuration);
    }

}