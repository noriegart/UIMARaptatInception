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
package de.tudarmstadt.ukp.inception.curation.sidebar;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CURATION_USER;
import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.enabledWhen;
import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.uima.cas.CAS;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Check;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.CheckGroup;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.value.AttributeMap;
import org.apache.wicket.util.value.IValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.agilecoders.wicket.core.markup.html.bootstrap.form.BootstrapRadioChoice;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.Role;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxButton;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.page.MergeDialog;
import de.tudarmstadt.ukp.inception.curation.CurationMetadata;
import de.tudarmstadt.ukp.inception.curation.CurationService;
import de.tudarmstadt.ukp.inception.curation.merge.AutomaticMergeStrategy;
import de.tudarmstadt.ukp.inception.curation.merge.ManualMergeStrategy;
import de.tudarmstadt.ukp.inception.curation.merge.MergeStrategy;

public class CurationSidebar
    extends AnnotationSidebar_ImplBase
{
    private static final long serialVersionUID = -4195790451286055737L;

    private Logger log = LoggerFactory.getLogger(getClass());

    private @SpringBean UserDao userRepository;
    private @SpringBean ProjectService projectService;
    private @SpringBean AnnotationEditorExtensionRegistry extensionRegistry;
    private @SpringBean CurationService curationService;
    private @SpringBean DocumentService documentService;

    private @SpringBean(name = ManualMergeStrategy.BEAN_NAME) MergeStrategy manualMergeStrat;
    private @SpringBean(name = AutomaticMergeStrategy.BEAN_NAME) MergeStrategy autoMergeStrat;

    private CheckGroup<User> selectedUsers;
    private final Form<List<User>> usersForm;
    private final Form<Void> settingsForm;
    private BootstrapRadioChoice<String> curationTargetChoice;
    private WebMarkupContainer mainContainer;
    private ListView<User> users;
    private CheckBox showAll;

    private final Label noDocsLabel;
    private final Label finishedLabel;

    private final MergeDialog mergeConfirm;

    public CurationSidebar(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider,
            AnnotationPage aAnnotationPage)
    {
        super(aId, aModel, aActionHandler, aCasProvider, aAnnotationPage);

        // init. state with stored curation user
        AnnotatorState state = aModel.getObject();
        String currentUsername = userRepository.getCurrentUsername();

        mainContainer = new WebMarkupContainer("mainContainer");
        mainContainer.setOutputMarkupId(true);
        mainContainer.add(visibleWhen(this::isCuratable));
        add(mainContainer);

        WebMarkupContainer notCuratableNotice = new WebMarkupContainer("notCuratableNotice");
        notCuratableNotice.setOutputMarkupId(true);
        notCuratableNotice.add(visibleWhen(() -> !isCuratable()));
        add(notCuratableNotice);

        // Add empty space message
        noDocsLabel = new Label("noDocumentsLabel", new ResourceModel("noDocuments"));
        finishedLabel = new Label("finishedLabel", new StringResourceModel("finished", this,
                LoadableDetachableModel.of(state::getUser)));
        finishedLabel.setOutputMarkupPlaceholderTag(true);
        mainContainer.add(finishedLabel);
        finishedLabel
                .add(visibleWhen(() -> curationService.isCurationFinished(state, currentUsername)));
        noDocsLabel.add(
                visibleWhen(() -> !finishedLabel.isVisible() && users.getModelObject().isEmpty()));
        mainContainer.add(noDocsLabel);

        // set up user-checklist
        usersForm = createUserSelection();
        usersForm.setOutputMarkupId(true);
        mainContainer.add(usersForm);

        // set up settings form for curation target
        settingsForm = createSettingsForm("settingsForm");
        settingsForm.setOutputMarkupId(true);
        settingsForm.setVisible(false);
        mainContainer.add(settingsForm);

        // confirmation dialog when using automatic merging (might change user's annos)
        IModel<String> documentNameModel = PropertyModel.of(getAnnotationPage().getModel(),
                "document.name");
        add(mergeConfirm = new MergeDialog("mergeConfirmDialog",
                new StringResourceModel("mergeConfirmTitle", this),
                new StringResourceModel("mergeConfirmText", this)
                        .setModel(getAnnotationPage().getModel()).setParameters(documentNameModel),
                documentNameModel));
        mainContainer.add(mergeConfirm);

        // add toggle for settings
        mainContainer.add(new AjaxLink<Void>("toggleOptionsVisibility")
        {
            private static final long serialVersionUID = -5535838955781542216L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                settingsForm.setVisible(!settingsForm.isVisible());
                aTarget.add(mainContainer);
            }
        });

        // user started curating, extension can show suggestions
        state.setMetaData(CurationMetadata.CURATION_USER_PROJECT, true);

        usersForm.add(enabledWhen(() -> !finishedLabel.isVisible()));
    }

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        if (!isCuratable()) {
            return;
        }

        String currentUsername = userRepository.getCurrentUsername();
        AnnotatorState state = getModelObject();

        // If curation is possible and the curation target user is different from the user set in
        // the annotation state, then we need to update the state and reload.
        User curationUser = curationService.retrieveCurationUser(currentUsername,
                state.getProject().getId());
        if (!state.getUser().equals(curationUser)) {
            state.setUser(curationUser);
            // Optional<AjaxRequestTarget> target =
            // RequestCycle.get().find(AjaxRequestTarget.class);
            // getAnnotationPage().actionLoadDocument(target.orElseGet(null));
            new RestartResponseException(getPage());
        }
    }

    private boolean isCuratable()
    {
        // Curation sidebar is not allowed when viewing another users annotations
        String currentUsername = userRepository.getCurrentUsername();
        AnnotatorState state = getModelObject();
        return asList(CURATION_USER, currentUsername).contains(state.getUser().getUsername());
    }

    private Form<Void> createSettingsForm(String aId)
    {
        Form<Void> form = new Form<Void>(aId);
        LambdaAjaxButton<Void> applyBtn = new LambdaAjaxButton<>("apply",
                this::updateForNewCurator);
        form.add(applyBtn);

        // set up curation target selection as radio button
        List<String> curationTargets = asList(CURATION_USER, userRepository.getCurrentUsername());
        ChoiceRenderer<String> choiceRenderer = new ChoiceRenderer<String>()
        {
            private static final long serialVersionUID = -8165699251116827372L;

            @Override
            public Object getDisplayValue(String aUsername)
            {
                if (aUsername.equals(CURATION_USER)) {
                    return " curation document";
                }
                else {
                    return " my document";
                }
            }
        };
        curationTargetChoice = new BootstrapRadioChoice<String>("curationTargetRadioBtn",
                Model.of(curationService.retrieveCurationTarget(
                        userRepository.getCurrentUser().getUsername(),
                        getModelObject().getProject().getId())),
                curationTargets, choiceRenderer)
        {
            private static final long serialVersionUID = 1513847274470368949L;

            @Override
            protected IValueMap getAdditionalAttributesForLabel(int aIndex, String aChoice)
            {
                // use normal font for choices
                IValueMap attrValMap = super.getAdditionalAttributesForLabel(aIndex, aChoice);
                if (attrValMap == null) {
                    attrValMap = new AttributeMap();
                }
                attrValMap.put("style", "font-weight:normal");
                return attrValMap;
            }
        };
        form.add(curationTargetChoice);

        showAll = new CheckBox("showAll",
                Model.of(curationService.isShowAll(userRepository.getCurrentUsername(),
                        getModelObject().getProject().getId())));
        showAll.setOutputMarkupPlaceholderTag(true);
        form.add(showAll);

        return form;
    }

    private void merge(AjaxRequestTarget aTarget, Form<Void> aForm)
    {
        AnnotatorState state = getModelObject();
        User currentUser = userRepository.getCurrentUser();
        updateCurator(state, currentUser);
        // update selected users
        updateUsers(state);
        long projectId = state.getProject().getId();
        curationService.updateMergeStrategy(currentUser.getUsername(), projectId, autoMergeStrat);
        mergeConfirm.setConfirmAction((target, form) -> {
            boolean mergeIncomplete = form.getModelObject().isMergeIncompleteAnnotations();
            doMerge(state, state.getUser().getUsername(), selectedUsers.getModelObject(),
                    mergeIncomplete);
            target.add(getAnnotationPage());
        });
        mergeConfirm.show(aTarget);
    }

    private void updateCurator(AnnotatorState aState, User aCurrentUser)
    {
        long project = aState.getProject().getId();
        User curator = aCurrentUser;
        String currentUsername = aCurrentUser.getUsername();
        // update curation target
        if (curationTargetChoice.getModelObject().equals(currentUsername)) {
            curationService.updateCurationName(currentUsername, project, currentUsername);
        }
        else {
            curator = new User(CURATION_USER, Role.ROLE_USER);
            curationService.updateCurationName(currentUsername, project, CURATION_USER);
        }

        aState.setUser(curator);
        aState.getSelection().clear();
    }

    private void doMerge(AnnotatorState aState, String aCurator, Collection<User> aUsers,
            boolean aMergeIncomplete)
    {
        // merge cases
        try {
            SourceDocument doc = aState.getDocument();
            Map<String, CAS> userCases = curationService.retrieveUserCases(aUsers, doc);
            Optional<CAS> targetCas = curationService.retrieveCurationCAS(aCurator,
                    aState.getProject().getId(), doc);
            if (targetCas.isPresent()) {
                MergeStrategy mergeStrat = curationService.retrieveMergeStrategy(
                        userRepository.getCurrentUsername(), aState.getProject().getId());
                mergeStrat.merge(aState, targetCas.get(), userCases, aMergeIncomplete);
                log.debug("{} merge done", mergeStrat.getUiName());
            }
        }
        catch (IOException e) {
            log.error(String.format("Could not retrieve CAS for user %s and project %d", aCurator,
                    aState.getProject().getId()));
            e.printStackTrace();
        }
    }

    private Form<List<User>> createUserSelection()
    {
        Form<List<User>> form = new Form<List<User>>("usersForm",
                LoadableDetachableModel.of(this::listSelectedUsers));
        form.add(new LambdaAjaxButton<>("clear", this::clearUsers));
        form.add(new LambdaAjaxButton<>("show", this::selectAndShow));
        form.add(new LambdaAjaxButton<>("merge", this::merge));
        selectedUsers = new CheckGroup<User>("selectedUsers", form.getModelObject());
        users = new ListView<User>("users", LoadableDetachableModel.of(this::listUsers))
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<User> aItem)
            {
                aItem.add(new Check<User>("user", aItem.getModel()));
                aItem.add(new Label("name", maybeAnonymizeUsername(aItem)));
            }
        };
        selectedUsers.add(users);
        form.add(selectedUsers);
        form.add(visibleWhen(() -> !noDocsLabel.isVisible() && !finishedLabel.isVisible()));
        return form;
    }

    private IModel<String> maybeAnonymizeUsername(ListItem<User> aUserListItem)
    {
        Project project = getModelObject().getProject();
        if (project.isAnonymousCuration()
                && !projectService.isManager(project, userRepository.getCurrentUser())) {
            return Model.of("Anonymized annotator " + (aUserListItem.getIndex() + 1));
        }

        return aUserListItem.getModel().map(User::getUiName);
    }

    private List<User> listSelectedUsers()
    {
        return curationService.listUsersSelectedForCuration(userRepository.getCurrentUsername(),
                getModelObject().getProject().getId()).orElse(emptyList());
    }

    /**
     * retrieve annotators of this document which finished annotating
     */
    private List<User> listUsers()
    {
        User currentUser = userRepository.getCurrentUser();
        String curatorName = getModelObject().getUser().getUsername();
        return curationService
                .listFinishedUsers(getModelObject().getProject(), getModelObject().getDocument())
                .stream()
                .filter(user -> !user.equals(currentUser) || curatorName.equals(CURATION_USER))
                .collect(Collectors.toList());
    }

    private void selectAndShow(AjaxRequestTarget aTarget, Form<Void> aForm)
    {
        // switch to manual merge
        AnnotatorState state = getModelObject();
        curationService.updateMergeStrategy(userRepository.getCurrentUsername(),
                state.getProject().getId(), manualMergeStrat);

        updateUsers(state);
        aTarget.add(usersForm);
        getAnnotationPage().actionRefreshDocument(aTarget);
    }

    private void updateForNewCurator(AjaxRequestTarget aTarget, Form<Void> aForm)
    {
        // updateCurator and merge strategy
        AnnotatorState state = getModelObject();
        User currentUser = userRepository.getCurrentUser();
        updateCurator(state, currentUser);
        curationService.updateMergeStrategy(currentUser.getUsername(), state.getProject().getId(),
                manualMergeStrat);
        curationService.setShowAll(currentUser.getUsername(), state.getProject().getId(),
                showAll.getModelObject());
        updateUsers(state);
        // close settingsForm after apply was pressed
        settingsForm.setVisible(false);
        // open curation doc
        getAnnotationPage().actionLoadDocument(aTarget);
    }

    private void updateUsers(AnnotatorState aState)
    {
        Collection<User> users = selectedUsers.getModelObject();
        curationService.updateUsersSelectedForCuration(userRepository.getCurrentUsername(),
                aState.getProject().getId(), users);
    }

    private void clearUsers(AjaxRequestTarget aTarget, Form<Void> aForm)
    {
        AnnotatorState state = getModelObject();
        selectedUsers.setModelObject(new ArrayList<>());
        curationService.clearUsersSelectedForCuration(userRepository.getCurrentUsername(),
                state.getProject().getId());
        aTarget.add(usersForm);
        getAnnotationPage().actionRefreshDocument(aTarget);
    }
}
