package net.kencochrane.raven.log4j;

import com.google.common.base.Joiner;
import mockit.Expectations;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.SentryException;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Date;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;

public class SentryAppenderEventBuildingTest {
    private SentryAppender sentryAppender;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockRaven);
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = "cdc23028-9b1f-485d-90bf-853d2f5f52d6";
        final String message = "fae94de0-0df3-4f96-92d5-a3fb66e714f3";
        final String threadName = "78ecbf4d-aa61-4dd7-8ef4-b1c49232e8f4";
        final Date date = new Date(1373883196416L);
        new Expectations() {{
            mockLogger.getName();
            result = loggerName;
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, date.getTime(), Level.INFO, message, threadName,
                null, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
        }};
        assertNoErrorsInErrorHandler();
    }

    @DataProvider(name = "levels")
    private Object[][] levelConversions() {
        return new Object[][]{
                {Event.Level.DEBUG, Level.TRACE},
                {Event.Level.DEBUG, Level.DEBUG},
                {Event.Level.INFO, Level.INFO},
                {Event.Level.WARNING, Level.WARN},
                {Event.Level.ERROR, Level.ERROR},
                {Event.Level.FATAL, Level.FATAL}};
    }

    @Test(dataProvider = "levels")
    public void testLevelConversion(final Event.Level expectedLevel, Level level) throws Exception {
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, level, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception("027a0db3-fb98-4377-bafb-fe5a49f067e8");

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, exception));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                    .get(ExceptionInterface.EXCEPTION_INTERFACE);
            SentryException sentryException = exceptionInterface.getExceptions().getFirst();
            assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
            assertThat(sentryException.getStackTraceInterface().getStackTrace(), is(exception.getStackTrace()));
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        final String extraKey = "1aeb7253-6e0d-4902-86d6-7e4b36571cfd";
        final String extraValue = "b2e19866-08a2-4611-b72c-150fa6aa3394";

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, null, Collections.singletonMap(extraKey, extraValue)));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertNoErrorsInErrorHandler();
    }
    
    @Test
    public void testMappedMdcAddedToTags() throws Exception {
        final String mappedKey = "User";
        final String mappedValue = "Test";
        
        sentryAppender.setMappedTags("User,foo");
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, null, Collections.singletonMap(mappedKey, mappedValue)));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(mappedKey, mappedValue));
            assertThat(event.getTags(), Matchers.<String, Object>hasEntry(mappedKey, mappedValue));

        }};
        assertNoErrorsInErrorHandler();
    }

    
    
    

    @Test
    public void testNdcAddedToExtra() throws Exception {
        final String ndcEntries = Joiner.on(' ').join("930580ba-f92f-4893-855b-ac24efa1a6c2",
                "fa32ad74-a015-492a-991f-c6a0e04accaf", "be9dd914-3690-4781-97b2-fe14aedb4cbd");

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, ndcEntries, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_NDC, ndcEntries));
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testSourceUsedAsStacktrace(@Injectable final LocationInfo locationInfo) throws Exception {
        final String className = "8004ac1e-8bd3-4762-abe0-2d0d79ae4e40";
        final String methodName = "ce7cd195-9e6d-4315-b883-12951be3da6e";
        final String fileName = "1ab50f43-f11c-4439-a05c-d089281411fa";
        final int line = 42;
        new NonStrictExpectations() {{
            locationInfo.getClassName();
            result = className;
            locationInfo.getMethodName();
            result = methodName;
            locationInfo.getFileName();
            result = fileName;
            locationInfo.getLineNumber();
            result = Integer.toString(line);
            setField(locationInfo, "fullInfo", "");
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, locationInfo, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            StackTraceInterface stackTraceInterface = (StackTraceInterface) event.getSentryInterfaces()
                    .get(StackTraceInterface.STACKTRACE_INTERFACE);
            assertThat(stackTraceInterface.getStackTrace(), arrayWithSize(1));
            assertThat(stackTraceInterface.getStackTrace()[0],
                    is(new StackTraceElement(className, methodName, fileName, line)));
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCulpritWithSource(@Injectable final LocationInfo locationInfo) throws Exception {
        final String className = "a";
        final String methodName = "b";
        final String fileName = "c";
        final int line = 42;
        new NonStrictExpectations() {{
            locationInfo.getClassName();
            result = className;
            locationInfo.getMethodName();
            result = methodName;
            locationInfo.getFileName();
            result = fileName;
            locationInfo.getLineNumber();
            result = Integer.toString(line);
            setField(locationInfo, "fullInfo", "");
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, locationInfo, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is("a.b(c:42)"));
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        final String loggerName = "2b27da1d-e03a-4292-9a81-78be5491a7e1";
        new Expectations() {{
            mockLogger.getName();
            result = loggerName;
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is(loggerName));
        }};
        assertNoErrorsInErrorHandler();
    }
}
