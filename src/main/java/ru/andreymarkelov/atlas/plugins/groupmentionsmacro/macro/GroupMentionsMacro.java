package ru.andreymarkelov.atlas.plugins.groupmentionsmacro.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.content.render.xhtml.XhtmlException;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.ContentPermissionManager;
import com.atlassian.confluence.core.DefaultSaveContext;
import com.atlassian.confluence.core.Modification;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.ContentPermission;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.confluence.xhtml.api.MacroDefinition;
import com.atlassian.confluence.xhtml.api.MacroDefinitionUpdater;
import com.atlassian.confluence.xhtml.api.XhtmlContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.atlassian.confluence.content.render.xhtml.Streamables.from;
import static com.atlassian.confluence.content.render.xhtml.definition.RichTextMacroBody.withStorage;
import static com.atlassian.confluence.user.AuthenticatedUserThreadLocal.get;

public class GroupMentionsMacro implements Macro {
    private final String baseUrl;
    private final PageManager pageManager;
    private final XhtmlContent xhtmlContent;
    private final UserAccessor userAccessor;
    private final ContentPermissionManager contentPermissionManager;

    public GroupMentionsMacro(
            SettingsManager settingsManager,
            PageManager pageManager,
            XhtmlContent xhtmlContent,
            UserAccessor userAccessor,
            ContentPermissionManager contentPermissionManager) {
        this.baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
        this.pageManager = pageManager;
        this.xhtmlContent = xhtmlContent;
        this.userAccessor = userAccessor;
        this.contentPermissionManager = contentPermissionManager;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext context) throws MacroExecutionException {
        final Collection<ConfluenceUser> pageViewerGroupUsers = new ArrayList<>();
        for (ConfluenceUser groupUser : userAccessor.getMembers(userAccessor.getGroup(parameters.get("group")))) {
            if (contentPermissionManager.hasContentLevelPermission(groupUser, ContentPermission.VIEW_PERMISSION, context.getEntity())) {
                pageViewerGroupUsers.add(groupUser);
            }
        }

        try {
            final MacroDefinition currentMacroDefinition = (MacroDefinition) context.getProperty("macroDefinition");
            final ContentEntityObject ceo = context.getEntity();
            final String modifiedBody = xhtmlContent.updateMacroDefinitions(ceo.getBodyAsString(), context, new MacroDefinitionUpdater() {
                @Override
                public MacroDefinition update(MacroDefinition macroDefinition) {
                    if (!macroDefinition.getMacroId().equals(currentMacroDefinition.getMacroId())) {
                        return macroDefinition;
                    }

                    StringBuffer sb = new StringBuffer();
                    for (ConfluenceUser pageViewerGroupUser : pageViewerGroupUsers) {
                        sb.append(renderUserStorage(pageViewerGroupUser));
                    }

                    if (macroDefinition.getBody() == null || macroDefinition.getBody().getBody().length() == 0) {
                        macroDefinition.setBody(withStorage(from(sb.toString())));
                    }
                    return macroDefinition;
                }
            });

            pageManager.saveNewVersion(ceo, new Modification<ContentEntityObject>() {
                @Override
                public void modify(ContentEntityObject contentEntityObject) {
                    if (contentEntityObject instanceof Page) {
                        ((Page) contentEntityObject).setParentPage(((Page) contentEntityObject).getParent());
                    }
                    contentEntityObject.setBodyAsString(modifiedBody);
                }
            }, new DefaultSaveContext(true, true, false));
        } catch (XhtmlException e) {
            throw new MacroExecutionException(e);
        }

        StringBuilder sb = new StringBuilder();
        if (pageViewerGroupUsers != null) {
            for (ConfluenceUser pageViewerGroupUser : pageViewerGroupUsers) {
                sb.append(renderUserHtml(pageViewerGroupUser)).append(" ");
            }
        }
        return sb.toString();
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.INLINE;
    }

    private String renderUserStorage(ConfluenceUser user) {
        return "<ac:link><ri:user ri:userkey=\"" + user.getKey().getStringValue() + "\"/></ac:link>";
    }

    private String renderUserHtml(ConfluenceUser user) {
        String currentUserClass = get().equals(user) ? "current-user-mention" : "";
        return "<a class=\"confluence-userlink user-mention " + currentUserClass + "\" data-username=\""
                + user.getName() + "\" href=\"/confluence/display/~" + user.getName() + "\" data-linked-resource-id=\""
                + user.getKey().getStringValue()
                + "\" data-linked-resource-version=\"1\" data-linked-resource-type=\"userinfo\" data-base-url=\""
                + baseUrl + "\">" + user.getFullName() + "</a>";
    }
}
