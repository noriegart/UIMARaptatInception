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
package de.tudarmstadt.ukp.inception.websocket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderTaskEvent;
import de.tudarmstadt.ukp.inception.websocket.model.LoggedEventMessage;

@ConditionalOnBean(SimpMessagingTemplate.class)
@Controller
public class RecommendationEventMessageControllerImpl
    implements RecommendationEventMessageController
{
    private final Logger log = LoggerFactory
            .getLogger(RecommendationEventMessageControllerImpl.class);

    public static final String REC_EVENTS = "/recEvents";
    public static final String REC_EVENTS_TOPIC = "/queue" + REC_EVENTS;

    private final SimpMessagingTemplate msgTemplate;

    public RecommendationEventMessageControllerImpl(@Autowired SimpMessagingTemplate aMsgTemplate)
    {
        msgTemplate = aMsgTemplate;
    }

    @EventListener
    @Override
    public void onRecommenderErrorEvent(RecommenderTaskEvent aEvent)
    {
        String errorMsg = aEvent.getErrorMsg();
        if (errorMsg == null) {
            return;
        }
        Recommender recommender = aEvent.getRecommender();
        Project project = recommender.getProject();
        LoggedEventMessage eventMsg = new LoggedEventMessage(aEvent.getUser(), project.getName(),
                aEvent.getTimestamp(), aEvent.getClass().getSimpleName());
        eventMsg.setEventMsg("[" + recommender.getName() + "] " + errorMsg);

        String channel = REC_EVENTS_TOPIC + "/" + project.getId();
        log.debug("Sending websocket event: {} '{}' to {}.", eventMsg.getEventType(),
                eventMsg.getEventMsg(), channel);
        msgTemplate.convertAndSendToUser(aEvent.getUser(), channel, eventMsg);
    }

    @Override
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable exception)
    {
        return exception.getMessage();
    }
}
