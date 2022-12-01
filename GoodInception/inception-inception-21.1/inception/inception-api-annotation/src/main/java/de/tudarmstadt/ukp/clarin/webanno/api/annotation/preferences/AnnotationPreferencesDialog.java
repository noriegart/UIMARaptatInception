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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.preferences;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.model.IModel;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.AjaxCallback;

/**
 * Dialog providing access to the per-user annotation preferences.
 */
public class AnnotationPreferencesDialog
    extends ModalWindow
{
    private static final long serialVersionUID = -6911254813496835955L;

    private boolean closeButtonClicked;

    private IModel<AnnotatorState> state;

    private AjaxCallback onChangeAction;

    public AnnotationPreferencesDialog(String id, final IModel<AnnotatorState> aModel)
    {
        super(id);

        state = aModel;

        // dialog window to select annotation layer preferences
        setInitialWidth(600);
        setInitialHeight(450);
        setResizable(true);
        setWidthUnit("px");
        setHeightUnit("px");
        setTitle("Preferences");
        setCssClassName("w_blue w_flex");
        setCloseButtonCallback((target) -> {
            closeButtonClicked = true;
            return true;
        });
    }

    @Override
    public void show(IPartialPageRequestHandler aTarget)
    {
        closeButtonClicked = false;

        setWindowClosedCallback((target) -> {
            if (!closeButtonClicked) {
                onConfirmInternal(target);
            }
        });

        setContent(new AnnotationPreferencesDialogContent(getContentId(), this, state)
        {
            private static final long serialVersionUID = -3434069761864809703L;

            @Override
            protected void onCancel(AjaxRequestTarget aTarget)
            {
                closeButtonClicked = true;
            }
        });

        super.show(aTarget);
    }

    public AjaxCallback getOnChangeAction()
    {
        return onChangeAction;
    }

    public void setOnChangeAction(AjaxCallback aOnChangeAction)
    {
        onChangeAction = aOnChangeAction;
    }

    protected void onConfirmInternal(AjaxRequestTarget aTarget)
    {
        boolean closeOk = true;

        // Invoke callback if one is defined
        if (onChangeAction != null) {
            try {
                onChangeAction.accept(aTarget);
            }
            catch (Exception e) {
                // LoggerFactory.getLogger(getPage().getClass()).error("Error: " + e.getMessage(),
                // e);
                // state.feedback = "Error: " + e.getMessage();
                // aTarget.add(getContent());
                closeOk = false;
            }
        }

        if (closeOk) {
            close(aTarget);
        }
    }
}
