/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.websocket;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.event.SpanCreatedEvent;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.Role;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.log.EventRepository;
import de.tudarmstadt.ukp.inception.log.adapter.EventLoggingAdapterRegistryImpl;
import de.tudarmstadt.ukp.inception.log.adapter.SpanEventAdapter;
import de.tudarmstadt.ukp.inception.websocket.controller.LoggedEventMessageControllerImpl;
import de.tudarmstadt.ukp.inception.websocket.model.LoggedEventMessage;

@ExtendWith(SpringExtension.class)
public class LoggedEventMessageControllerImplTest
{
    private static final String TEST_ADMIN_USERNAME = "testAdmin";
    private @Mock DocumentService docService;
    private @Mock ProjectService projectService;
    private @Mock EventRepository eventRepository;
    private EventLoggingAdapterRegistryImpl adapterRegistry;
    private TestChannel outboundChannel;

    private Project testProject;
    private SourceDocument testDoc;
    private User testAdmin;

    private LoggedEventMessageControllerImpl sut;

    @BeforeEach
    public void setup()
    {
        outboundChannel = new TestChannel();
        adapterRegistry = new EventLoggingAdapterRegistryImpl(asList(new SpanEventAdapter()));
        adapterRegistry.onContextRefreshedEvent(null);
        testProject = new Project("test-project");
        testProject.setId(1L);
        testDoc = new SourceDocument("testDoc", testProject, "text");
        testDoc.setId(2L);
        testAdmin = new User(TEST_ADMIN_USERNAME, Role.ROLE_USER, Role.ROLE_ADMIN);

        when(projectService.getProject(1L)).thenReturn(testProject);
        when(docService.getSourceDocument(1L, 2L)).thenReturn(testDoc);

        sut = new LoggedEventMessageControllerImpl(new SimpMessagingTemplate(outboundChannel),
                adapterRegistry, docService, projectService, eventRepository);
    }

    @Test
    @WithMockUser(TEST_ADMIN_USERNAME)
    public void thatSpanCreatedEventIsRelayedToUser()
    {
        sut.onApplicationEvent(
                new SpanCreatedEvent(getClass(), testDoc, testAdmin.getUsername(), null, null));

        List<Message<?>> messages = outboundChannel.getMessages();
        LoggedEventMessage msg = (LoggedEventMessage) messages.get(0).getPayload();

        assertThat(messages).hasSize(1);
        assertThat(msg.getDocumentName()).isEqualTo(testDoc.getName());
        assertThat(msg.getProjectName()).isEqualTo(testProject.getName());
        assertThat(msg.getActorName()).isEqualTo(testAdmin.getUsername());
        assertThat(msg.getEventType()).isEqualTo(SpanCreatedEvent.class.getSimpleName());
    }

    @SpringBootConfiguration
    public static class SpringConfig
    {
    }

    public class TestChannel
        extends AbstractMessageChannel
    {

        private List<Message<?>> messages = new ArrayList<>();

        @Override
        protected boolean sendInternal(Message<?> aMessage, long aTimeout)
        {
            messages.add(aMessage);
            return true;
        }

        public List<Message<?>> getMessages()
        {
            return messages;
        }

        public void clearMessages()
        {
            messages.clear();
        }
    }
}
