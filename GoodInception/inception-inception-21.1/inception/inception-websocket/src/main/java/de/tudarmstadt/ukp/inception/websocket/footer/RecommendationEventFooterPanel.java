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
package de.tudarmstadt.ukp.inception.websocket.footer;

import static org.apache.wicket.markup.head.JavaScriptHeaderItem.forReference;

import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.wicket.Page;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.authorization.Action;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeAction;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.agilecoders.wicket.webjars.request.resource.WebjarsJavaScriptResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ProjectPageBase;
import de.tudarmstadt.ukp.inception.support.vue.VueComponent;
import de.tudarmstadt.ukp.inception.websocket.config.WebsocketConfig;
import de.tudarmstadt.ukp.inception.websocket.controller.RecommendationEventMessageControllerImpl;
import de.tudarmstadt.ukp.inception.websocket.feedback.FeedbackPanelExtensionBehavior;

@AuthorizeAction(action = Action.RENDER, roles = "ROLE_USER")
public class RecommendationEventFooterPanel
    extends VueComponent
{
    private static final Logger log = LoggerFactory.getLogger(RecommendationEventFooterPanel.class);
    private static final long serialVersionUID = 1L;

    private FeedbackPanelExtensionBehavior feedback;
    private @SpringBean ServletContext servletContext;

    public RecommendationEventFooterPanel(String aId)
    {
        super(aId, "RecommendationEventFooterPanel.vue");
        feedback = new FeedbackPanelExtensionBehavior();
        add(feedback);

    }

    @Override
    protected void onConfigure()
    {
        super.onConfigure();
        // model will be added as props to vue component
        setDefaultModel(Model.ofMap(Map.of("wsEndpoint", constructEndpointUrl(), "topicChannel",
                RecommendationEventMessageControllerImpl.REC_EVENTS, "feedbackPanelId",
                feedback.retrieveFeedbackPanelId(this), "projectId", getProjectId())));
    }

    private long getProjectId()
    {
        Page page = null;
        try {
            page = getPage();
        }
        catch (WicketRuntimeException e) {
            log.debug("No page yet.");
        }

        if (page == null || !(page instanceof ProjectPageBase)) {
            return -1;
        }

        Project project = ((ProjectPageBase) page).getProject();
        if (project == null) {
            return -1;
        }

        return project.getId();
    }

    private String constructEndpointUrl()
    {
        Url endPointUrl = Url.parse(String.format("%s%s", servletContext.getContextPath(),
                WebsocketConfig.WS_ENDPOINT));
        endPointUrl.setProtocol("ws");
        String fullUrl = RequestCycle.get().getUrlRenderer().renderFullUrl(endPointUrl);
        return fullUrl;
    }

    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        super.renderHead(aResponse);
        aResponse.render(forReference(new WebjarsJavaScriptResourceReference(
                "webstomp-client/current/dist/webstomp.min.js")));
    }

}
