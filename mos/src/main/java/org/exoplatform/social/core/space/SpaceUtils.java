/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.portlet.PortletPreferences;
import javax.servlet.http.HttpServletRequest;

import org.exoplatform.application.registry.Application;
import org.exoplatform.application.registry.ApplicationCategory;
import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfig;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.ApplicationState;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NavigationServiceException;
import org.exoplatform.portal.mop.navigation.NavigationState;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.user.UserNavigation;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.mop.user.UserPortal;
import org.exoplatform.portal.mop.user.UserPortalContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.portal.webui.workspace.UIWorkingWorkspace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipHandler;
import org.exoplatform.services.organization.MembershipType;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.gatein.common.i18n.LocalizedString;
import org.gatein.common.util.Tools;
import org.gatein.pc.api.Portlet;
import org.gatein.pc.api.PortletInvoker;
import org.gatein.pc.api.info.MetaInfo;
import org.gatein.pc.api.info.PortletInfo;

import com.ibm.icu.text.Transliterator;

/**
 * SpaceUtils Utility for working with space
 */
public class SpaceUtils {
  private static final Log LOG = ExoLogger.getLogger(SpaceUtils.class);

  public static final String SPACE_GROUP = "/spaces";

  public static final String PLATFORM_USERS_GROUP = "/platform/users";

  public static final String MANAGER = "manager";
  
  public static final String MEMBER = "member";

  public static final String MENU_CONTAINER = "Menu";

  public static final String APPLICATION_CONTAINER = "Application";

  public static final String SPACE_URL = "SPACE_URL";

  private static final ConcurrentHashMap<String, Application> appListCache = new ConcurrentHashMap<String,
          Application>();

  private static final String REMOTE_CATEGORY_NAME = "remote";

  // A {@link Transliterator} instance is stateless which has for consequences that it is Thread Safe
  // and thus can be shared among several threads as mentioned in the javadoc
  private static final Transliterator ACCENTS_CONVERTER = Transliterator.getInstance("Latin; NFD; [:Nonspacing " +
          "Mark:] Remove; NFC;");

  /**
   * Creates a new group from an existing group. This new group will get all data from existing group except for group
   * name
   *
   * @param parentGroup
   * @param existingGroup
   * @param name
   * @return new Group
   * @throws Exception
   * @deprecated to be removed by 1.2.x
   */
  @SuppressWarnings("unchecked")
  public static Group createGroupFromExistingGroup(Group parentGroup,
                                                   Group existingGroup,
                                                   String name) throws Exception {
    OrganizationService orgSrc = getOrganizationService();
    GroupHandler groupHandler = orgSrc.getGroupHandler();
    MembershipHandler memberShipHandler = orgSrc.getMembershipHandler();
    Group newGroup = groupHandler.createGroupInstance();
    newGroup.setGroupName(name);
    newGroup.setLabel(name);
    newGroup.setDescription(existingGroup.getDescription());
    groupHandler.addChild(parentGroup, newGroup, true);
    Collection<Membership> memberShips = memberShipHandler.findMembershipsByGroup(existingGroup);
    Iterator<Membership> itr = memberShips.iterator();
    while (itr.hasNext()) {
      Membership membership = itr.next();
      User user = orgSrc.getUserHandler().findUserByName(membership.getUserName());
      MembershipType memberShipType = orgSrc.getMembershipTypeHandler()
              .findMembershipType(membership.getMembershipType());
      memberShipHandler.linkMembership(user, newGroup, memberShipType, true);
    }
    return newGroup;
  }

  /**
   * Gets applications that a group has right to access
   *
   * @param groupId
   * @return applications
   * @throws Exception
   */
  public static List<Application> getApplications(String groupId) throws Exception {

    List<Application> list = new CopyOnWriteArrayList<Application>();
    ApplicationRegistryService appRegistrySrc = getApplicationRegistryService();

    List<ApplicationCategory> listCategory = appRegistrySrc.getApplicationCategories();
    Iterator<ApplicationCategory> cateItr = listCategory.iterator();
    while (cateItr.hasNext()) {
      ApplicationCategory cate = cateItr.next();
      if (!hasAccessPermission(cate, groupId)) {
        cateItr.remove();
        continue;
      }
      ApplicationType<org.exoplatform.portal.pom.spi.portlet.Portlet> portletType = ApplicationType.PORTLET;
      List<Application> applications = appRegistrySrc.getApplications(cate, portletType);
      Iterator<Application> appIterator = applications.iterator();
      while (appIterator.hasNext()) {
        Application app = appIterator.next();
        if (!hasAccessPermission(app, groupId)) {
          appIterator.remove();
        } else {
          list.add(app);
        }
      }
    }
    return list;
  }

  /**
   * Gets appStore of HashMap type with key = ApplicationCategory and value = list of applications. appStore is filter
   * by access permission from that group; filter by application category access permission and filtered by application
   * permission.
   *
   * @param space
   * @return appStore
   * @throws Exception
   */
  public static Map<ApplicationCategory, List<Application>> getAppStore(Space space) throws Exception {
    Map<ApplicationCategory, List<Application>> appStore = new LinkedHashMap<ApplicationCategory, List<Application>>();
    ApplicationRegistryService appRegistryService = getApplicationRegistryService();
    String groupId = space.getGroupId();
    List<ApplicationCategory> categoryList = appRegistryService.getApplicationCategories();
    Collections.sort(categoryList, new PortletCategoryComparator());
    Iterator<ApplicationCategory> cateItr = categoryList.iterator();
    while (cateItr.hasNext()) {
      ApplicationCategory appCategory = cateItr.next();
      if (!hasAccessPermission(appCategory, groupId)) {
        continue;
      }
      List<Application> tempAppList = new ArrayList<Application>();
      List<Application> appList = appCategory.getApplications();
      Collections.sort(appList, new PortletComparator());
      Iterator<Application> appItr = appList.iterator();
      while (appItr.hasNext()) {
        Application application = appItr.next();
        if (!hasAccessPermission(application, groupId)) {
          continue;
        }
        tempAppList.add(application);
      }
      if (tempAppList.size() > 0) {
        appStore.put(appCategory, tempAppList);
      }
    }
    return appStore;
  }

  /**
   * Gets application from portal container. This is used to get application when get application by applicationRegistry
   * return null.
   *
   * @param appId
   * @return An application has name match input appId.
   * @throws Exception
   */
  public static Application getAppFromPortalContainer(String appId) throws Exception {
    if (appListCache.containsKey(appId)) {
      return appListCache.get(appId);
    }

    ExoContainer container = ExoContainerContext.getCurrentContainer();

    PortletInvoker portletInvoker = (PortletInvoker) container.getComponentInstance(PortletInvoker.class);
    Set<Portlet> portlets = portletInvoker.getPortlets();
    ApplicationRegistryService appRegistryService = getApplicationRegistryService();
    for (Portlet portlet : portlets) {
      PortletInfo info = portlet.getInfo();
      String portletApplicationName = info.getApplicationName();
      String portletName = info.getName();

      portletApplicationName = portletApplicationName.replace('/', '_');
      portletName = portletName.replace('/', '_');

      if (portletName.equals(appId)) {
        LocalizedString keywordsLS = info.getMeta().getMetaValue(MetaInfo.KEYWORDS);

        String[] categoryNames = null;
        if (keywordsLS != null) {
          String keywords = keywordsLS.getDefaultString();
          if (keywords != null && keywords.length() != 0) {
            categoryNames = keywords.split(",");
          }
        }

        if (categoryNames == null || categoryNames.length == 0) {
          categoryNames = new String[]{portletApplicationName};
        }

        if (portlet.isRemote()) {
          categoryNames = Tools.appendTo(categoryNames, REMOTE_CATEGORY_NAME);
        }

        for (String categoryName : categoryNames) {
          ApplicationCategory category;

          categoryName = categoryName.trim();

          category = appRegistryService.getApplicationCategory(categoryName);
          if (category == null) {
            category = new ApplicationCategory();
            category.setName(categoryName);
            category.setDisplayName(categoryName);
          }

          Application app = appRegistryService.getApplication(categoryName + "/" + portletName);
          if (app != null) {
            return app;
          }

          // ContentType<?> contentType;
          String contentId;

          LocalizedString descriptionLS = portlet.getInfo()
                  .getMeta()
                  .getMetaValue(MetaInfo.DESCRIPTION);
          LocalizedString displayNameLS = portlet.getInfo()
                  .getMeta()
                  .getMetaValue(MetaInfo.DISPLAY_NAME);

          getLocalizedStringValue(descriptionLS, portletName);

          app = new Application();

          if (portlet.isRemote()) {
            // contentType = WSRP.CONTENT_TYPE;
            contentId = portlet.getContext().getId();
          } else {
            contentId = info.getApplicationName() + "/" + info.getName();
          }
          app.setType(ApplicationType.PORTLET);
          app.setContentId(contentId);
          app.setApplicationName(portletName);
          app.setCategoryName(categoryName);
          app.setDisplayName(getLocalizedStringValue(displayNameLS, portletName));
          app.setDescription(getLocalizedStringValue(descriptionLS, portletName));
          Application oldApp = appListCache.putIfAbsent(app.getApplicationName(), app);
          return oldApp == null ? app : oldApp;
        }
      }
    }

    return null;
  }

  /**
   * PortletCategoryComparator
   */
  static class PortletCategoryComparator implements Comparator<ApplicationCategory> {
    public int compare(ApplicationCategory cat1, ApplicationCategory cat2) {
      return cat1.getDisplayName().compareTo(cat2.getDisplayName());
    }
  }

  /**
   * PortletComparator
   */
  static class PortletComparator implements Comparator<Application> {
    public int compare(Application p1, Application p2) {
      return p1.getDisplayName().compareTo(p2.getDisplayName());
    }
  }

  /**
   * Utility for cleaning space name
   *
   * @param str
   * @return cleaned string
   */
  public static String cleanString(String str) {

    str = ACCENTS_CONVERTER.transliterate(str);

    // the character ? seems to not be changed to d by the transliterate
    // function

    StringBuilder cleanedStr = new StringBuilder(str.trim());
    // delete special character
    for (int i = 0; i < cleanedStr.length(); i++) {
      char c = cleanedStr.charAt(i);
      if (c == ' ') {
        if (i > 0 && cleanedStr.charAt(i - 1) == '_') {
          cleanedStr.deleteCharAt(i--);
        } else {
          c = '_';
          cleanedStr.setCharAt(i, c);
        }
        continue;
      }

      if (!(Character.isLetterOrDigit(c) || c == '_')) {
        cleanedStr.deleteCharAt(i--);
        continue;
      }

      if (i > 0 && c == '_' && cleanedStr.charAt(i - 1) == '_') {
        cleanedStr.deleteCharAt(i--);
      }
    }
    return cleanedStr.toString().toLowerCase();
  }

  /**
   * Gets spaceName by portletPreference.
   *
   * @return
   */
  public static String getSpaceUrl() {
    PortletRequestContext pcontext = (PortletRequestContext) WebuiRequestContext.getCurrentInstance();
    PortletPreferences pref = pcontext.getRequest().getPreferences();
    return pref.getValue(SPACE_URL, "");
  }

  /**
   * change spaceUrl preferences for all applications in a pageNode. This pageNode is the clonedPage of spacetemplate.
   *
   * @param spacePageNode
   * @param space
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  public static void changeSpaceUrlPreference(UserNode spacePageNode,
                                              Space space,
                                              String newSpaceName) throws Exception {
    String pageId = spacePageNode.getPageRef();
    DataStorage dataStorage = getDataStorage();
    Page page = dataStorage.getPage(pageId);
    page.setTitle(page.getTitle().replace(space.getDisplayName(), newSpaceName));
    dataStorage.save(page);
    ArrayList<ModelObject> pageChildren = page.getChildren();
    Container menuContainer = findContainerById(pageChildren, MENU_CONTAINER);

    org.exoplatform.portal.config.model.Application<org.exoplatform.portal.pom.spi.portlet.Portlet> menuPortlet =
    (org.exoplatform.portal.config.model.Application<org.exoplatform.portal.pom.spi.portlet.Portlet>) menuContainer
              .getChildren()
              .get(0);

    ApplicationState<org.exoplatform.portal.pom.spi.portlet.Portlet> menuState = menuPortlet.getState();
    org.exoplatform.portal.pom.spi.portlet.Portlet menuPortletPreference;
    try {
      menuPortletPreference = dataStorage.load(menuState, ApplicationType.PORTLET);
      menuPortletPreference.setValue(SPACE_URL, space.getUrl());
      dataStorage.save(menuState, menuPortletPreference);
    } catch (Exception e) {
      LOG.warn("Can not save menu portlet preference!", e);
    }

    Container applicationContainer = findContainerById(pageChildren, APPLICATION_CONTAINER);

    try {
      org.exoplatform.portal.config.model.Application<org.exoplatform.portal.pom.spi.portlet.Portlet> applicationPortlet =
      (org.exoplatform.portal.config.model.Application<org.exoplatform.portal.pom.spi.portlet.Portlet>) applicationContainer
              .getChildren()
              .get(0);
      ApplicationState<org.exoplatform.portal.pom.spi.portlet.Portlet> appState = applicationPortlet.getState();
      org.exoplatform.portal.pom.spi.portlet.Portlet appPortletPreference;
      try {
        appPortletPreference = dataStorage.load(appState, ApplicationType.PORTLET);
        if (appPortletPreference == null || appPortletPreference.getPreference(SPACE_URL) == null) {
          return;
        } else {
          appPortletPreference.setValue(SPACE_URL, space.getUrl());
        }
        dataStorage.save(appState, appPortletPreference);
      } catch (Exception e) {
        LOG.warn("Can not save application portlet preference!", e);
      }

    } catch (Exception e) {
      // ignore it? exception will happen when this is gadgetApplicationType
    }
  }

  /**
   * end the request and push data to JCR.
   */
  public static void endRequest() {
    RequestLifeCycle.end();
    //SOC-2124 too long wait for executing these handlers which handles when space created.
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    //Need to begin here for RequestLifeCycle.end(); if is not existing, an exception will be appeared. 
    RequestLifeCycle.begin(container);
  }
  /**
   * Finds container by id
   *
   * @param children
   * @param id
   * @return
   */
  public static Container findContainerById(ArrayList<ModelObject> children, String id) {
    Container found = null;
    for (Object obj : children) {
      if (org.exoplatform.portal.config.model.Application.class.isInstance(obj)) {
        continue;
      }
      Container child = (Container) obj;
      if (child.getId() == null) {
        found = findContainerById(child.getChildren(), id);
        if (found != null) {
          return found;
        }
      } else {
        if (child.getId().equals(id)) {
          return child;
        } else {
          found = findContainerById(child.getChildren(), id);
        }
        if (found != null) {
          return found;
        }
      }
    }
    return found;
  }

  /**
   * Utility for setting navigation. Set pageNavigation, if existed in portal navigations, reset; if not, added to
   * portal navigations.
   *
   * @param nav
   * TODO This method which uses to cache the Navigation. Maybe remove this method because it
   * uses to cache for UI
   */
  public static void setNavigation(UserNavigation nav) {
    if (nav == null) {
      return;
    }
    WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
    if (context == null) {
      return;
    }
    try {
      UserNode selectedNav = Util.getUIPortal().getSelectedUserNode();
      if (selectedNav.getId() == nav.getKey().getName()) {
        Util.getUIPortal().setNavPath(selectedNav);
      }
    } catch (Exception e) {
      LOG.warn(e.getMessage(), e);
    }
  }

  /**
   * Utility for removing portal navigation.
   *
   * @param nav
   * @throws Exception
   */
  public static void removeNavigation(UserNavigation nav) throws Exception {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    NavigationService navService = (NavigationService) container.getComponentInstanceOfType(NavigationService.class);
    try {
      navService.destroyNavigation(new NavigationContext(nav.getKey(), new NavigationState(1)));
    } catch (NavigationServiceException nex) {
      LOG.warn("Failed to remove navigations", nex);
    } catch (Exception e) {
      LOG.warn("Failed to remove navigations", e);
    } 
  }

  /**
   * Reload all portal navigations synchronizing navigations and stores in currentAccessibleSpaces BUG: SOC-406,
   * SOC-134
   *
   * @throws Exception
   */
  public static void reloadNavigation() throws Exception {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    SpaceService spaceService = (SpaceService) container.getComponentInstanceOfType(SpaceService.class);
    String userId = Util.getPortalRequestContext().getRemoteUser();
    List<Space> spaces = spaceService.getAccessibleSpaces(userId);
    UserPortalConfig userPortalConfig = Util.getUIPortalApplication().getUserPortalConfig();
    UserPortal userPortal = userPortalConfig.getUserPortal();
    List<UserNavigation> navs = userPortal.getNavigations();
    List<UserNavigation> spaceNavs = new ArrayList<UserNavigation>();
    String ownerId;
    for (UserNavigation nav : navs) {
      ownerId = nav.getKey().getName();
      try {
        Space space = spaceService.getSpaceByGroupId(ownerId);
        if (space != null) {
          spaceNavs.add(nav);
        }
      } catch (Exception e) {
        LOG.warn("Fail with getSpaceByGroupId by ownerId: " + ownerId + " in reloadNavigation", e);
      }
    }
    String groupId;
    // add new space navigation
    boolean spaceContained = false;
    for (Space space : spaces) {
      groupId = space.getGroupId();
      for (UserNavigation nav : spaceNavs) {
        if (groupId.equals(nav.getKey().getName())) {
          spaceContained = true;
          break;
        }
      }
      if (spaceContained == false) {
        setNavigation(getGroupNavigation(groupId));
      }
    }
    // remove deleted space navigation
    if (spaces.size() == 0) {
      // remove all navs
      for (UserNavigation nav : spaceNavs) {
        removeNavigation(nav);
      }
    } else {
      boolean navContained = false;
      for (UserNavigation nav : spaceNavs) {
        for (Space space : spaces) {
          groupId = space.getGroupId();
          if (groupId.equals(nav.getKey().getName())) {
            navContained = true;
            break;
          }
        }
        if (navContained == false) {
          removeNavigation(nav);
        }
      }
    }
  }

  /**
   * Updates working work space
   */
  public static void updateWorkingWorkSpace() {
    UIPortalApplication uiPortalApplication = Util.getUIPortalApplication();
    UIWorkingWorkspace uiWorkingWS = uiPortalApplication.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
    PortalRequestContext pContext = Util.getPortalRequestContext();
    pContext.addUIComponentToUpdateByAjax(uiWorkingWS);
    pContext.setFullRender(true);
  }

  /**
   * Creates new group in /Spaces node and return groupId
   *
   * @param spaceName String
   * @param creator   String
   * @return groupId String
   * @throws SpaceException
   */
  public static String createGroup(String spaceName, String creator) throws SpaceException {

    OrganizationService organizationService = getOrganizationService();
    GroupHandler groupHandler = organizationService.getGroupHandler();
    Group parentGroup;
    Group newGroup;
    String groupId;
    String shortName;
    try {
      parentGroup = groupHandler.findGroupById(SPACE_GROUP);
      // Creates new group
      newGroup = groupHandler.createGroupInstance();
      shortName = SpaceUtils.cleanString(spaceName);
      groupId = parentGroup.getId() + "/" + shortName;
      if (isSpaceNameExisted(spaceName)) {
        throw new SpaceException(SpaceException.Code.SPACE_ALREADY_EXIST);
      }
      newGroup.setGroupName(shortName);
      newGroup.setLabel(spaceName);
      newGroup.setDescription("the " + parentGroup.getId() + "/" + shortName + " group");
      groupHandler.addChild(parentGroup, newGroup, true);
    } catch (Exception e) {
      if (e instanceof SpaceException) {
        throw (SpaceException) e;
      }
      throw new SpaceException(SpaceException.Code.UNABLE_TO_CREATE_GROUP, e);
    }

    try {
      // adds user as creator (manager)
      addCreatorToGroup(creator, groupId);
    } catch (Exception e) {
      // TODO:should rollback what has to be rollback here
      throw new SpaceException(SpaceException.Code.UNABLE_TO_ADD_CREATOR, e);
    }
    return groupId;
  }

  /**
   * Removes a group owning a space
   *
   * @param space
   * @throws SpaceException
   */
  public static void removeGroup(Space space) throws SpaceException {
    try {
      OrganizationService organizationService = getOrganizationService();
      GroupHandler groupHandler = organizationService.getGroupHandler();
      Group group = groupHandler.findGroupById(space.getGroupId());
      groupHandler.removeGroup(group, true);
    } catch (Exception e) {
      throw new SpaceException(SpaceException.Code.UNABLE_TO_REMOVE_GROUP, e);
    }
  }

  /**
   * Checks if a space exists
   *
   * @param spaceName
   * @return boolean if existed return true, else return false
   * @throws SpaceException with code INTERNAL_SERVER_ERROR
   */
  public static boolean isSpaceNameExisted(String spaceName) throws SpaceException {
    PortalContainer portalContainer = PortalContainer.getInstance();
    SpaceService spaceService = (SpaceService) portalContainer.getComponentInstanceOfType(SpaceService.class);
    // Checks whether spaceName has existed yet
    if (spaceService.getSpaceByPrettyName(cleanString(spaceName)) != null) {
      return true;
    }
    return false;
  }

  /**
   * When user chooses an existing group, that user will be added to that group as a manager
   *
   * @param creator String
   * @param groupId String
   * @throws SpaceException with code UNABLE_TO_ADD_CREATOR
   */
  public static void addCreatorToGroup(String creator, String groupId) {
    addUserToGroupWithMembership(creator, groupId, MANAGER);
  }

  /**
   * Adds the user to group with the membership (member, manager).
   * 
   * @param remoteId
   * @param groupId
   * @param membership
   * @since 1.2.0-GA
   */
  private static void addUserToGroupWithMembership(String remoteId, String groupId, String membership) {
    OrganizationService organizationService = getOrganizationService();
    try {
      // TODO: checks whether user is already manager?
      User user = organizationService.getUserHandler().findUserByName(remoteId);
      MembershipType membershipType = organizationService.getMembershipTypeHandler().findMembershipType(membership);
      GroupHandler groupHandler = organizationService.getGroupHandler();
      Group existingGroup = groupHandler.findGroupById(groupId);
      organizationService.getMembershipHandler().linkMembership(user, existingGroup, membershipType, true);
    } catch (Exception e) {
      LOG.warn("Unable to add user: " + remoteId + " to group: " + groupId + " with membership: " + membership);
    }
  }
  
  /**
   * Adds the user to group with the membership (member). 
   * 
   * @param remoteId
   * @param groupId
   * @since 1.2.0-GA
   */
  public static void addUserToGroupWithMemberMembership(String remoteId, String groupId) {
    addUserToGroupWithMembership(remoteId, groupId, MEMBER);
  }
  
  /**
   * Adds the user to group with the membership (manager). 
   * 
   * @param remoteId
   * @param groupId
   * @since 1.2.0-GA
   */
  public static void addUserToGroupWithManagerMembership(String remoteId, String groupId) {
    addUserToGroupWithMembership(remoteId, groupId, MANAGER);
  }
  
  /**
   * Removes the user from group with the membership (member, manager). 
   * 
   * @param remoteId
   * @param groupId
   * @param membership
   * @since 1.2.0-GA
   */
  private static void removeUserFromGroupWithMembership(String remoteId, String groupId, String membership) {
    try {
      OrganizationService organizationService = getOrganizationService();
      MembershipHandler memberShipHandler = organizationService.getMembershipHandler();
      if (MEMBER.equals(membership)) {
          Collection<Membership> memberships = memberShipHandler.findMembershipsByUserAndGroup(remoteId, groupId);
          if (memberships.size() == 0) {
            LOG.warn("User " + remoteId + " is not member");
          }
          Iterator<Membership> itr = memberships.iterator();
          while (itr.hasNext()) {
            Membership mbShip = itr.next();
            memberShipHandler.removeMembership(mbShip.getId(), true);
          }
      } else if (MANAGER.equals(membership)) {
          Membership memberShip = memberShipHandler.findMembershipByUserGroupAndType(remoteId, groupId, MANAGER);
          if (memberShip == null) {
            LOG.warn("User: " + remoteId + " is not manager of group: ");
            return;
          }
          UserHandler userHandler = organizationService.getUserHandler();
          User user = userHandler.findUserByName(remoteId);
          memberShipHandler.removeMembership(memberShip.getId(), true);
          MembershipType mbShipTypeMember = organizationService.getMembershipTypeHandler().findMembershipType(MEMBER);
          GroupHandler groupHandler = organizationService.getGroupHandler();
          memberShipHandler.linkMembership(user, groupHandler.findGroupById(groupId), mbShipTypeMember, true);
      }
    } catch (Exception e) {
      LOG.warn("Failed to remove user: " + remoteId + " to group: " + groupId + " with membership: " + membership, e);
    }
  }
  
  /**
   * Removes the user from group with member membership. 
   * 
   * @param remoteId
   * @param groupId
   * @since 1.2.0-GA
   */
  public static void removeUserFromGroupWithMemberMembership(String remoteId, String groupId) {
    removeUserFromGroupWithMembership(remoteId, groupId, MEMBER);
  }
  
  /**
   * Removes the user from group with manager membership.
   * 
   * @param remoteId
   * @param groupId
   * @since 1.2.0-GA
   */
  public static void removeUserFromGroupWithManagerMembership(String remoteId, String groupId) {
    removeUserFromGroupWithMembership(remoteId, groupId, MANAGER);
  }
  
  /**
   * Creates group navigation if not existed or return existing group navigation based on groupId
   *
   * @param groupId String
   * @return spaceNav PageNavigation
   * @throws SpaceException
   */
  public static UserNavigation createGroupNavigation(String groupId) throws SpaceException {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    NavigationService navService = (NavigationService) container.getComponentInstance(NavigationService.class);
    //14-june-2011 Apply UserNavigation
    //PageNavigation spaceNav;
    UserNavigation spaceNav = null;
    UserPortal userPortal = getUserPortal();
    try {
      //spaceNav = dataStorage.getPageNavigation(PortalConfig.GROUP_TYPE, groupId);
      spaceNav = getUserPortal().getNavigation(SiteKey.group(groupId));
      if (spaceNav == null) {
        // creates new space navigation
        NavigationContext navContext = new NavigationContext(SiteKey.group(groupId), new NavigationState(1));
        navService.saveNavigation(navContext);
        spaceNav = userPortal.getNavigation(SiteKey.group(groupId));
      }
      
      //SOC-2016 this fragment code makes sure SpaceNavigation can not be NULL when it returns.
      if (spaceNav == null) {
        SpaceUtils.endRequest();
        userPortal.refresh();
        spaceNav = userPortal.getNavigation(SiteKey.group(groupId));
      }
      return spaceNav;
    } catch (Exception e) {
      // TODO:should rollback what has to be rollback here
      throw new SpaceException(SpaceException.Code.UNABLE_TO_CREAT_NAV, e);
    }
  }
  
  /**
   * Using this method to get the UserPortal make sure that the data is latest.
   * It's will remove the caching.
   * @return
   */
  public static UserPortal getUserPortal() {
    try {
      UIPortalApplication uiApp = Util.getUIPortalApplication();
      return uiApp.getUserPortalConfig().getUserPortal();
    } catch (Exception e) {
      //Makes sure that in the RestService still gets the UserPortal.
      try {
        return getUserPortalForRest();
      } catch (Exception e1) {
        return null;
      }
    }
  }
 
  /**
   * Gets the UserPortal when uses the RestService.
   * @return
   * @throws Exception
   */
  public static UserPortal getUserPortalForRest() throws Exception {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    UserPortalConfigService userPortalConfigSer = (UserPortalConfigService)container.getComponentInstanceOfType(UserPortalConfigService.class);

    UserPortalContext NULL_CONTEXT = new UserPortalContext() {
      public ResourceBundle getBundle(UserNavigation navigation) {
        return null;
      }

      public Locale getUserLocale() {
        return Locale.ENGLISH;
      }
    };
    
    String remoteId = ConversationState.getCurrent().getIdentity().getUserId();
    UserPortalConfig userPortalCfg = userPortalConfigSer.getUserPortalConfig(userPortalConfigSer.getDefaultPortal(), remoteId, NULL_CONTEXT);
    return userPortalCfg.getUserPortal();
  }

  /**
   * Removes group navigations.
   *
   * @param groupId
   * @throws SpaceException
   */
  public static void removeGroupNavigation(String groupId) throws SpaceException {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    NavigationService navService = (NavigationService) container.getComponentInstanceOfType(NavigationService.class);
    try {
      NavigationContext nav = navService.loadNavigation(SiteKey.group(groupId));
      if (nav != null) {
        navService.destroyNavigation(nav);
      } else {
        throw new Exception("spaceNav is null");
      }
    } catch (Exception e) {
      throw new SpaceException(SpaceException.Code.UNABLE_TO_REMOVE_NAV, e);
    }

  }

  /**
   * Gets userNavigation by a space's groupId
   *
   * @param groupId
   * @throws Exception
   * @throws Exception
   */
  public static UserNavigation getGroupNavigation(String groupId) throws Exception {
    return getUserPortal().getNavigation(SiteKey.group(groupId));
  }

  /**
   * This related to a bug from portal. When this bug is resolved, use userNavigation.getNode(space.getUrl());
   *
   * @param userNavigation
   * @param spaceUrl
   * @return
   */
  public static UserNode getHomeNode(UserNavigation userNavigation, String spaceUrl) {
    //Need to get usernode base on resolvePath
    return getUserPortal().resolvePath(userNavigation, null, spaceUrl);
  }
  
  /**
   * Retrieving the UserNode base on the UserNavigation
   *
   * @param userNavigation
   * @return
   */
  public static UserNode getHomeNode(UserNavigation userNavigation) {
    return getUserPortal().getNode(userNavigation, Scope.SINGLE, null, null);
  }
  
  /**
   * Retrieving the UserNode with Children base on the spaceUrl and UserNavigation.
   * When user can use this method to get homeNode, you can not call the update node
   *  to getChildren()
   *  
   * @param userNavigation
   * @param spaceUrl
   * @return
   */
  public static UserNode getHomeNodeWithChildren(UserNavigation userNavigation, String spaceUrl) {
    //Need to get usernode base on resolvePath
    UserNode homeNode = getUserPortal().resolvePath(userNavigation, null, spaceUrl);
    getUserPortal().updateNode(homeNode, Scope.CHILDREN, null);
    return homeNode;
    
  }

  /**
   * Sorts spaces list by priority and alphabet order
   *
   * @param spaces
   * @return ordered spaces list
   */
  public static List<Space> getOrderedSpaces(List<Space> spaces) {
    Iterator<Space> itr = spaces.iterator();
    List<Space> orderedSpaces = new ArrayList<Space>();
    List<Space> middleSpaces = new ArrayList<Space>();
    List<Space> lowSpaces = new ArrayList<Space>();
    Space space = null;
    while (itr.hasNext()) {
      space = itr.next();
      String priority = space.getPriority();
      if (priority.equals(Space.HIGH_PRIORITY)) {
        orderedSpaces.add(space);
      } else if (priority.equals(Space.INTERMEDIATE_PRIORITY)) {
        middleSpaces.add(space);
      } else if (priority.equals(Space.LOW_PRIORITY)) {
        lowSpaces.add(space);
      }
    }
    for (Space sp : middleSpaces) {
      orderedSpaces.add(sp);
    }
    for (Space sp : lowSpaces) {
      orderedSpaces.add(sp);
    }
    return orderedSpaces;
  }

  /**
   * Utility for counting the number of members in a space
   *
   * @param space
   * @return the number of members
   * @throws SpaceException
   */
  public static int countMembers(Space space) throws SpaceException {
    if (space.getMembers() != null) {
      return space.getMembers().length;
    }
    return 0;
  }

  /**
   * Gets app status in a space
   *
   * @param space
   * @param appId
   * @return appStatus
   */
  public static String getAppStatus(Space space, String appId) {
    String installedApps = space.getApp();
    if (installedApps == null) {
      return null;
    }
    if (installedApps.contains(appId)) {
      String appStatusPattern = getAppStatusPattern(installedApps, appId);
      return appStatusPattern.split(":")[3];
    }
    return null;
  }

  /**
   * Gets appNodeName in a space by its appId
   *
   * @param space
   * @param appId
   * @return
   */
  public static String getAppNodeName(Space space, String appId) {
    String installedApps = space.getApp();
    if (installedApps == null) {
      return null;
    }
    if (installedApps.contains(appId)) {
      String appStatusPatern = getAppStatusPattern(installedApps, appId);
      return appStatusPatern.split(":")[0];
    }
    return null;
  }

  /**
   * Checks if an application is removable or not. Default will return true.
   *
   * @param space
   * @param appId
   */
  public static boolean isRemovableApp(Space space, String appId) {
    String installedApps = space.getApp();
    if (installedApps.contains(appId)) {
      String appStatus = getAppStatusPattern(installedApps, appId);
      String[] spliter = appStatus.split(":");
      if (spliter[2].equals("false")) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets all application id from a space
   *
   * @param space
   * @return
   */
  public static List<String> getAppIdList(Space space) {
    List<String> appIdList = new ArrayList<String>();
    String installedApps = space.getApp();
    if (installedApps != null) {
      if (installedApps.contains(",")) {
        String[] appStatuses = installedApps.split(",");
        for (String appStatus : appStatuses) {
          appIdList.add((appStatus.split(":"))[0]);
        }
      } else {
        appIdList.add((installedApps.split(":"))[0]);
      }
    }
    return appIdList;
  }

  /**
   * Utility for getting absolute url
   *
   * @return absolute url
   * @throws SpaceException
   */
  public static String getAbsoluteUrl() throws SpaceException {
    PortalRequestContext portalRequestContext = Util.getPortalRequestContext();
    HttpServletRequest request = portalRequestContext.getRequest();
    String str = request.getRequestURL().toString();
    return str.substring(0, str.indexOf(portalRequestContext.getRequestContextPath()));
  }

  /**
   * Checks if a user is removed or not.<br>
   *
   * @param userName User name for checking.
   * @throws SpaceException if user is removed.
   */
  public static void checkUserExisting(String userName) throws SpaceException {
    OrganizationService orgService = getOrganizationService();
    User user = null;
    try {
      user = orgService.getUserHandler().findUserByName(userName);
    } catch (Exception e) {
      throw new SpaceException(SpaceException.Code.ERROR_RETRIEVING_USER, e);
    }

    if (user == null) {
      throw new SpaceException(SpaceException.Code.ERROR_RETRIEVING_USER);
    }
  }

  /**
   * Check an application is installed or not yet.
   *
   * @param space
   * @param appId
   * @return
   */
  public static boolean isInstalledApp(Space space, String appId) {
    String installedApps = space.getApp();
    if (installedApps == null) {
      return false;
    }
    String[] apps = installedApps.split(",");
    String[] appPart;
    for (int idx = 0; idx < apps.length; idx++) {
      appPart = apps[idx].split(":");
      if (appPart[0].equals(appId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get display application name in formal.
   *
   * @param appDisplayName
   * @return
   */
  public static String getDisplayAppName(String appDisplayName) {
    int length = appDisplayName.length();
    if (appDisplayName.toLowerCase().endsWith("portlet")) {
      return appDisplayName.substring(0, length - 7).trim();
    }
    if (appDisplayName.toLowerCase().endsWith("gadget")) {
      return appDisplayName.substring(0, length - 6).trim();
    }
    return appDisplayName;
  }

  /**
   * Gets appStatusPattern: [appId:appNodeName:isRemovableString:status]
   *
   * @param installedApps
   * @param appId
   * @return
   */
  private static String getAppStatusPattern(String installedApps, String appId) {
    if (installedApps == null) {
      return null;
    }
    if (installedApps.contains(appId)) {
      String appStatus = installedApps.substring(installedApps.indexOf(appId));
      if (appStatus.contains(",")) {
        appStatus = appStatus.substring(0, appStatus.indexOf(","));
      }
      String[] splited = appStatus.split(":");
      if (splited.length != 4) {
        LOG.warn("appStatus is not in correct form of [appId:appNodeName:isRemovableString:status] : "
                + appStatus);
        return null;
      }
      return appStatus;
    }
    return null;
  }

  /**
   * Checks if a group can have access to an application
   *
   * @param app
   * @param groupId
   * @return
   * @throws Exception
   */
  private static boolean hasAccessPermission(Application app, String groupId) throws Exception {
    List<String> permissions = app.getAccessPermissions();
    if (permissions == null) {
      return false;
    }
    for (String ele : permissions) {
      if (hasViewPermission(ele, groupId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets Organization Service
   *
   * @return
   */
  public static OrganizationService getOrganizationService() {
    PortalContainer portalContainer = PortalContainer.getInstance();
    return (OrganizationService) portalContainer.getComponentInstanceOfType(OrganizationService.class);
  }

  /**
   * Gets dataStorage
   *
   * @return
   */
  public static DataStorage getDataStorage() {
    PortalContainer portalContainer = PortalContainer.getInstance();
    return (DataStorage) portalContainer.getComponentInstanceOfType(DataStorage.class);
  }

  /**
   * Checks if a group have access permission to an application category
   *
   * @param app
   * @param groupId
   * @return true if that group has access permission; otherwise, false
   * @throws Exception
   */
  private static boolean hasAccessPermission(ApplicationCategory app, String groupId) throws Exception {
    List<String> permissions = app.getAccessPermissions();
    if (permissions == null) {
      return false;
    }
    for (String ele : permissions) {
      if (hasViewPermission(ele, groupId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks view permission
   *
   * @param expPerm
   * @param groupId
   * @return
   * @throws Exception
   */
  private static boolean hasViewPermission(String expPerm, String groupId) throws Exception {
    if (UserACL.EVERYONE.equals(expPerm)) {
      return true;
    }
    String[] temp = expPerm.split(":");
    if (temp.length < 2) {
      return false;
    }
    String tempExp = temp[1].trim();
    if (tempExp.equals(groupId) || tempExp.equals(PLATFORM_USERS_GROUP)) {
      return true;
    }
    return false;
  }

  /**
   * Gets localized string value
   *
   * @param localizedString
   * @param portletName
   * @return
   */
  private static String getLocalizedStringValue(LocalizedString localizedString, String portletName) {
    if (localizedString == null || localizedString.getDefaultString() == null) {
      return portletName;
    } else {
      return localizedString.getDefaultString();
    }
  }

  /**
   * Gets application registry service
   *
   * @return
   */
  private static ApplicationRegistryService getApplicationRegistryService() {
    PortalContainer portalContainer = PortalContainer.getInstance();
    return (ApplicationRegistryService) portalContainer.getComponentInstanceOfType(ApplicationRegistryService.class);
  }
  
  /**
   * Filter all invalid character (anything except word, number, space and search wildcard) from Space search conditional.
   * @since: 1.2.2
   * @param input String
   * @return
   */

  public static String removeSpecialCharacterInSpaceFilter(String input){
    String result = input.replaceAll("[^\\pL\\pM\\p{Nd}\\p{Nl}\\p{Pc}[\\p{InEnclosedAlphanumerics}&&\\p{So}]\\?\\*%0-9]", " ");
    result = result.replaceAll("\\s+", " ");
    return result.trim();
  }
}
