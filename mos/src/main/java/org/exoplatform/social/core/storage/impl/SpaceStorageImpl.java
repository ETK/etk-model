/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.exoplatform.social.core.storage.impl;

import org.chromattic.api.ChromatticSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.chromattic.api.query.Query;
import org.chromattic.api.query.QueryBuilder;
import org.chromattic.api.query.QueryResult;
import org.chromattic.ext.ntdef.NTFile;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.chromattic.entity.IdentityEntity;
import org.exoplatform.social.core.chromattic.entity.SpaceEntity;
import org.exoplatform.social.core.chromattic.entity.SpaceListEntity;
import org.exoplatform.social.core.chromattic.entity.SpaceRef;
import org.exoplatform.social.core.chromattic.entity.SpaceRootEntity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.storage.SpaceStorageException;
import org.exoplatform.social.core.storage.api.SpaceStorage;
import org.exoplatform.social.core.storage.exception.NodeNotFoundException;
import org.exoplatform.social.core.storage.query.QueryFunction;
import org.exoplatform.social.core.storage.query.WhereExpression;

/**
 * Space storage layer.
 *
 * @author <a href="mailto:alain.defrance@exoplatform.com">Alain Defrance</a>
 * @version $Revision$
 */
public class SpaceStorageImpl extends AbstractStorage implements SpaceStorage {

  /** Logger */
  private static final Log LOG = ExoLogger.getLogger(SpaceStorageImpl.class);

  /**
   * The identity storage
   */
  private final IdentityStorageImpl identityStorage;

  /**
   * Constructor.
   *
   * @param identityStorage the identity storage
   */
  public SpaceStorageImpl(IdentityStorageImpl identityStorage) {
   this.identityStorage = identityStorage;
 }

  /**
   * Fills {@link Space}'s properties to {@link SpaceEntity}'s.
   *
   * @param entity the space entity from chromattic
   * @param space  the space pojo for services
   */
  private void fillSpaceFromEntity(SpaceEntity entity, Space space) {

    space.setApp(entity.getApp());
    space.setId(entity.getId());
    space.setPrettyName(entity.getPrettyName());
    space.setDisplayName(entity.getDisplayName());
    space.setRegistration(entity.getRegistration());
    space.setDescription(entity.getDescription());
    space.setType(entity.getType());
    space.setVisibility(entity.getVisibility());
    space.setPriority(entity.getPriority());
    space.setGroupId(entity.getGroupId());
    space.setUrl(entity.getURL());
    space.setMembers(entity.getMembersId());
    space.setManagers(entity.getManagerMembersId());
    space.setPendingUsers(entity.getPendingMembersId());
    space.setInvitedUsers(entity.getInvitedMembersId());

    if (entity.getAvatarLastUpdated() != null) {
      try {
        ChromatticSession chromatticSession = getSession();
        String url = String.format("/%s/jcr/%s/%s/production/soc:providers/soc:space/soc:%s/soc:profile/soc:avatar/?upd=%d",
                      container.getRestContextName(),
                      lifeCycle.getRepositoryName(),
                      chromatticSession.getJCRSession().getWorkspace().getName(),
                      entity.getPrettyName(),
                      entity.getAvatarLastUpdated());
        space.setAvatarUrl(LinkProvider.escapeJCRSpecialCharacters(url));
      } catch (Exception e) {
        LOG.warn("Failed to build avatar url: " + e.getMessage());
      }
    }
    space.setAvatarLastUpdated(entity.getAvatarLastUpdated());
  }

  /**
   * Fills {@link SpaceEntity}'s properties from {@link Space}'s.
   *
   * @param space the space pojo for services
   * @param entity the space entity from chromattic
   */
  private void fillEntityFromSpace(Space space, SpaceEntity entity) {

    entity.setApp(space.getApp());
    entity.setPrettyName(space.getPrettyName());
    entity.setDisplayName(space.getDisplayName());
    entity.setRegistration(space.getRegistration());
    entity.setDescription(space.getDescription());
    entity.setType(space.getType());
    entity.setVisibility(space.getVisibility());
    entity.setPriority(space.getPriority());
    entity.setGroupId(space.getGroupId());
    entity.setURL(space.getUrl());
    entity.setMembersId(space.getMembers());
    entity.setManagerMembersId(space.getManagers());
    entity.setPendingMembersId(space.getPendingUsers());
    entity.setInvitedMembersId(space.getInvitedUsers());
    entity.setAvatarLastUpdated(space.getAvatarLastUpdated());

  }

  /**
   * The reference types.
   */
  private enum RefType {
    MEMBER() {
      @Override
      public SpaceListEntity refsOf(IdentityEntity identityEntity) {
        return identityEntity.getSpaces();
      }},
    MANAGER() {
      @Override
      public SpaceListEntity refsOf(IdentityEntity identityEntity) {
        return identityEntity.getManagerSpaces();
      }},
    PENDING() {
      @Override
      public SpaceListEntity refsOf(IdentityEntity identityEntity) {
        return identityEntity.getPendingSpaces();
      }},
    INVITED() {
      @Override
      public SpaceListEntity refsOf(IdentityEntity identityEntity) {
        return identityEntity.getInvitedSpaces();
      }};

    public abstract SpaceListEntity refsOf(IdentityEntity identityEntity);
  }

  private class UpdateContext {
    private String[] added;
    private String[] removed;

    private UpdateContext(String[] added, String[] removed) {
      this.added = added;
      this.removed = removed;
    }

    public String[] getAdded() {
      return added;
    }

    public String[] getRemoved() {
      return removed;
    }
  }

  private String[] sub(String[] l1, String[] l2) {

    if (l1 == null) {
      return new String[]{};
    }

    if (l2 == null) {
      return l1;
    }

    List<String> l = new ArrayList(Arrays.asList(l1));
    l.removeAll(Arrays.asList(l2));
    return l.toArray(new String[]{});
  }

  private void createRefs(SpaceEntity spaceEntity, Space space) throws NodeNotFoundException {

    String[] removedMembers = sub(spaceEntity.getMembersId(), space.getMembers());
    String[] removedManagers = sub(spaceEntity.getManagerMembersId(), space.getManagers());
    String[] removedInvited = sub(spaceEntity.getInvitedMembersId(), space.getInvitedUsers());
    String[] removedPending = sub(spaceEntity.getPendingMembersId(), space.getPendingUsers());

    String[] addedMembers = sub(space.getMembers(), spaceEntity.getMembersId());
    String[] addedManagers = sub(space.getManagers(), spaceEntity.getManagerMembersId());
    String[] addedInvited = sub(space.getInvitedUsers(), spaceEntity.getInvitedMembersId());
    String[] addedPending = sub(space.getPendingUsers(), spaceEntity.getPendingMembersId());

    manageRefList(new UpdateContext(addedMembers, removedMembers), spaceEntity, RefType.MEMBER);
    manageRefList(new UpdateContext(addedManagers, removedManagers), spaceEntity, RefType.MANAGER);
    manageRefList(new UpdateContext(addedInvited, removedInvited), spaceEntity, RefType.INVITED);
    manageRefList(new UpdateContext(addedPending, removedPending), spaceEntity, RefType.PENDING);

  }

  private void manageRefList(UpdateContext context, SpaceEntity spaceEntity, RefType type) {

    if (context.getAdded() != null) {
      for (String userName : context.getAdded()) {
        try {
          IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userName);
          SpaceListEntity listRef = type.refsOf(identityEntity);
          SpaceRef ref = listRef.getRef(spaceEntity.getName());
          ref.setSpaceRef(spaceEntity);
        }
        catch (NodeNotFoundException e) {
          LOG.warn(e.getMessage(), e);
        }
      }

      for (String userName : context.getRemoved()) {
        try {
          IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userName);
          SpaceListEntity listRef = type.refsOf(identityEntity);
          SpaceRef ref = listRef.getRef(spaceEntity.getName());
          getSession().remove(ref);
        }
        catch (NodeNotFoundException e) {
          LOG.warn(e.getMessage(), e);
        }
      }
    }
  }

  private boolean validateFilter(SpaceFilter filter) {

    if (filter == null) {
      return false;
    }

    if (filter.getSpaceNameSearchCondition() != null &&
        filter.getSpaceNameSearchCondition().length() != 0) {
      return isValidInput(filter.getSpaceNameSearchCondition());
    }
    else if (!Character.isDigit(filter.getFirstCharacterOfSpaceName())) {
      return true;
    }
    return false;

  }

  private SpaceEntity createSpace(Space space) throws SpaceStorageException {

    SpaceRootEntity spaceRootEntity = getSpaceRoot();
    SpaceEntity spaceEntity = spaceRootEntity.getSpace(space.getPrettyName());
    space.setId(spaceEntity.getId());

    return spaceEntity;

  }

  private SpaceEntity _saveSpace(Space space) throws NodeNotFoundException {

    return _findById(SpaceEntity.class, space.getId());

  }

  private void _applyFilter(WhereExpression whereExpression, SpaceFilter spaceFilter) {

    String spaceNameSearchCondition = spaceFilter.getSpaceNameSearchCondition();
    char firstCharacterOfName = spaceFilter.getFirstCharacterOfSpaceName();

    if (spaceNameSearchCondition != null && spaceNameSearchCondition.length() != 0) {
      if (this.isValidInput(spaceNameSearchCondition)) {

        spaceNameSearchCondition = this.processSearchCondition(spaceNameSearchCondition);

        if (spaceNameSearchCondition.contains(StorageUtils.PERCENT_STR)) {
          whereExpression.startGroup();
          whereExpression
              .like(SpaceEntity.name, spaceNameSearchCondition)
              .or()
              .like(SpaceEntity.description, spaceNameSearchCondition);
          whereExpression.endGroup();
        }
        else {
          whereExpression.startGroup();
          whereExpression
              .contains(SpaceEntity.name, spaceNameSearchCondition)
              .or()
              .contains(SpaceEntity.description, spaceNameSearchCondition);
          whereExpression.endGroup();
        }
      }
    }
    else if (!Character.isDigit(firstCharacterOfName)) {
      String firstCharacterOfNameString = Character.toString(firstCharacterOfName);
      String firstCharacterOfNameLowerCase = firstCharacterOfNameString.toLowerCase() + StorageUtils.PERCENT_STR;
      whereExpression
          .like(whereExpression.callFunction(QueryFunction.LOWER, SpaceEntity.name), firstCharacterOfNameLowerCase);
    }
  }

  private boolean isValidInput(String input) {
    if (input == null || input.length() == 0) {
      return false;
    }
    String cleanString = input.replaceAll("\\*", "");
    cleanString = cleanString.replaceAll("\\%", "");
    if (cleanString.length() == 0) {
       return false;
    }
    return true;
  }

  private String processSearchCondition(String searchCondition) {
    StringBuffer searchConditionBuffer = new StringBuffer();
    if (!searchCondition.contains(StorageUtils.ASTERISK_STR) && !searchCondition.contains(StorageUtils.PERCENT_STR)) {
      if (searchCondition.charAt(0) != StorageUtils.ASTERISK_CHAR) {
        searchConditionBuffer.append(StorageUtils.ASTERISK_STR).append(searchCondition);
      }
      if (searchCondition.charAt(searchCondition.length() - 1) != StorageUtils.ASTERISK_CHAR) {
        searchConditionBuffer.append(StorageUtils.ASTERISK_STR);
      }
    } else {
      searchCondition = searchCondition.replace(StorageUtils.ASTERISK_STR, StorageUtils.PERCENT_STR);
      searchConditionBuffer.append(StorageUtils.PERCENT_STR).append(searchCondition).append(StorageUtils.PERCENT_STR);
    }
    return searchConditionBuffer.toString();
  }

  /*
    Filter query
   */

  private Query<SpaceEntity> _getSpacesByFilterQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    _applyFilter(whereExpression, spaceFilter);

    if (userId != null) {
      whereExpression
          .and()
          .equals(SpaceEntity.membersId, userId);
    }

    if (whereExpression.toString().length() == 0) {
      return builder.get();
    }
    else {
      return builder.where(whereExpression.toString()).get();
    }

  }

  private Query<SpaceEntity> getAccessibleSpacesByFilterQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    if (validateFilter(spaceFilter)) {
      _applyFilter(whereExpression, spaceFilter);
      whereExpression.and();
      whereExpression.startGroup();
    }

    whereExpression
        .equals(SpaceEntity.membersId, userId)
        .or()
        .equals(SpaceEntity.managerMembersId, userId);


    whereExpression.endAllGroup();

    return builder.where(whereExpression.toString()).get();

  }

  private Query<SpaceEntity> getPublicSpacesQuery(String userId) {
    return getPublicSpacesQuery(userId, null);
  }

  private Query<SpaceEntity> getPublicSpacesQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    if (validateFilter(spaceFilter)) {
      _applyFilter(whereExpression, spaceFilter);
      whereExpression.and();
    }

    builder.where(whereExpression
        .not().equals(SpaceEntity.membersId, userId)
        .and().not().equals(SpaceEntity.managerMembersId, userId)
        .and().not().equals(SpaceEntity.invitedMembersId, userId)
        .and().not().equals(SpaceEntity.pendingMembersId, userId)
        .toString()
    );

    return builder.where(whereExpression.toString()).get();

  }

  private Query<SpaceEntity> getPendingSpacesFilterQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    if (validateFilter(spaceFilter)) {
      _applyFilter(whereExpression, spaceFilter);
      whereExpression.and();
    }

    builder.where(whereExpression
        .equals(SpaceEntity.pendingMembersId, userId)
        .toString()
    );

    return builder.where(whereExpression.toString()).get();

  }

  private Query<SpaceEntity> getInvitedSpacesFilterQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    if (validateFilter(spaceFilter)) {
      _applyFilter(whereExpression, spaceFilter);
      whereExpression.and();
    }

    builder.where(whereExpression
        .equals(SpaceEntity.invitedMembersId, userId)
        .toString()
    );

    return builder.where(whereExpression.toString()).get();

  }

  private Query<SpaceEntity> getEditableSpacesFilterQuery(String userId, SpaceFilter spaceFilter) {

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    if (validateFilter(spaceFilter)) {
      _applyFilter(whereExpression, spaceFilter);
      whereExpression.and();
    }

    builder.where(whereExpression
        .equals(SpaceEntity.managerMembersId, userId)
        .toString()
    );

    return builder.where(whereExpression.toString()).get();

  }

  private Query<SpaceEntity> getSpacesByFilterQuery(SpaceFilter spaceFilter) {
    return _getSpacesByFilterQuery(null, spaceFilter);
  }

  /*
    Public
   */

  /**
   * {@inheritDoc}
   */
  public Space getSpaceByDisplayName(String spaceDisplayName) throws SpaceStorageException {
    Space space = null;

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    whereExpression.equals(SpaceEntity.displayName, spaceDisplayName);

    QueryResult<SpaceEntity> result = builder.where(whereExpression.toString()).get().objects();
    
    if (result.hasNext()) {
      space = new Space();
      fillSpaceFromEntity(result.next(), space);
    }

    return space;
  }

  /**
   * {@inheritDoc}
   */
  public void saveSpace(Space space, boolean isNew) throws SpaceStorageException {

    SpaceEntity entity;

    try {
      
      if (isNew) {
        entity = createSpace(space);
      }
      else {
        entity = _saveSpace(space);
      }

      //
      createRefs(entity, space);
      fillEntityFromSpace(space, entity);

      //
      getSession().save();

      //
      LOG.debug(String.format(
          "Space %s (%s) saved",
          space.getPrettyName(),
          space.getId()
      ));

    }
    catch (NodeNotFoundException e) {
      throw new SpaceStorageException(SpaceStorageException.Type.FAILED_TO_SAVE_SPACE, e.getMessage(), e);
    }

  }

  /**
   * {@inheritDoc}
   */
  public void deleteSpace(String id) throws SpaceStorageException {

    String name;

    //
    try {
      name = _findById(SpaceEntity.class, id).getPrettyName();
    }
    catch (NodeNotFoundException e) {
      throw new SpaceStorageException(SpaceStorageException.Type.FAILED_TO_DELETE_SPACE, e.getMessage());
    }

    //
    _removeById(SpaceEntity.class, id);

    //
    getSession().save();

    //
    LOG.debug(String.format(
        "Space %s removed",
        name)
    );
  }

  /*
    Member spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getMemberSpacesCount(String userId) throws SpaceStorageException {
    try {
       return identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId).getSpaces().getRefs().size();
    }
    catch (NodeNotFoundException e){
       return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public int getMemberSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {
    return _getSpacesByFilterQuery(userId, spaceFilter).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getMemberSpaces(String userId) throws SpaceStorageException {

    try {

      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);

      List<Space> spaces = new ArrayList<Space>();
      for (SpaceRef space : identityEntity.getSpaces().getRefs().values()) {

        Space newSpace = new Space();
        fillSpaceFromEntity(space.getSpaceRef(), newSpace);
        spaces.add(newSpace);
      }

      return spaces;

    }
    catch (NodeNotFoundException e) {
      throw new SpaceStorageException(SpaceStorageException.Type.FAILED_TO_GET_MEMBER_SPACES, e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getMemberSpaces(String userId, long offset, long limit) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {

      int i = 0;
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getSpaces().getRefs().values();

      if (spaceEntities != null) {

        Iterator<SpaceRef> it = spaceEntities.iterator();
        _skip(it, offset);

        while (it.hasNext()) {

          SpaceRef spaceRef = it.next();

          Space space = new Space();
          fillSpaceFromEntity(spaceRef.getSpaceRef(), space);
          spaces.add(space);

          if (++i >= limit) {
            return spaces;
          }
        }
      }

    }
    catch (NodeNotFoundException e) {
      return spaces;
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getMemberSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = _getSpacesByFilterQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;

  }

  /*
    Pending spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getPendingSpacesCount(String userId) throws SpaceStorageException {
    try {
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getPendingSpaces().getRefs().values();
      return spaceEntities.size();
    }
    catch (NodeNotFoundException e) {
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public int getPendingSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {
    return getPendingSpacesFilterQuery(userId, spaceFilter).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPendingSpaces(String userId) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getPendingSpaces().getRefs().values();

      for (SpaceRef ref : spaceEntities) {

        Space space = new Space();
        fillEntityFromSpace(space, ref.getSpaceRef());
        spaces.add(space);
      }
    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPendingSpaces(String userId, long offset, long limit) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {

      int i = 0;

      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getPendingSpaces().getRefs().values();

      if (spaceEntities != null) {

        Iterator<SpaceRef> it = spaceEntities.iterator();
        _skip(it, offset);

        while (it.hasNext()) {

          SpaceRef spaceRef = it.next();

          Space space = new Space();
          fillSpaceFromEntity(spaceRef.getSpaceRef(), space);
          spaces.add(space);

          if (++i >= limit) {
            return spaces;
          }
        }
      }

    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPendingSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getPendingSpacesFilterQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /*
    Invited spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getInvitedSpacesCount(String userId) throws SpaceStorageException {

    try {
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getInvitedSpaces().getRefs().values();
      return spaceEntities.size();
    }
    catch (NodeNotFoundException e) {
      return 0;
    }

  }

  /**
   * {@inheritDoc}
   */
  public int getInvitedSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {

    if (validateFilter(spaceFilter)) {
      return getInvitedSpacesFilterQuery(userId, spaceFilter).objects().size();
    }
    else {
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getInvitedSpaces(String userId) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getInvitedSpaces().getRefs().values();

      for (SpaceRef ref : spaceEntities) {

        Space space = new Space();
        fillEntityFromSpace(space, ref.getSpaceRef());
        spaces.add(space);
      }
    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getInvitedSpaces(String userId, long offset, long limit) throws SpaceStorageException {
    List<Space> spaces = new ArrayList<Space>();

    try {

      int i = 0;
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getInvitedSpaces().getRefs().values();

      if (spaceEntities != null) {

        Iterator<SpaceRef> it = spaceEntities.iterator();
        _skip(it, offset);

        while (it.hasNext()) {

          SpaceRef spaceRef = it.next();

          Space space = new Space();
          fillSpaceFromEntity(spaceRef.getSpaceRef(), space);
          spaces.add(space);

          if (++i >= limit) {
            return spaces;
          }
        }
      }

    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getInvitedSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getInvitedSpacesFilterQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /*
    Public spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getPublicSpacesCount(String userId) throws SpaceStorageException {
    return getPublicSpacesQuery(userId).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public int getPublicSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {
    if (validateFilter(spaceFilter)) {
      return getPublicSpacesQuery(userId, spaceFilter).objects().size();
    }
    else {
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPublicSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    try {
      identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
    }
    catch (NodeNotFoundException e) {
      userId = null;
    }

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getPublicSpacesQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPublicSpaces(String userId) throws SpaceStorageException {
    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getPublicSpacesQuery(userId).objects();

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getPublicSpaces(String userId, long offset, long limit) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getPublicSpacesQuery(userId).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /*
    Accessible spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getAccessibleSpacesCount(String userId) throws SpaceStorageException {
    return getAccessibleSpacesByFilterQuery(userId, null).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public int getAccessibleSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {
    return getAccessibleSpacesByFilterQuery(userId, spaceFilter).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getAccessibleSpaces(String userId) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getAccessibleSpacesByFilterQuery(userId, null).objects();

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getAccessibleSpaces(String userId, long offset, long limit) throws SpaceStorageException {
    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getAccessibleSpacesByFilterQuery(userId, null).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getAccessibleSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getAccessibleSpacesByFilterQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /*
    Editable spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getEditableSpacesCount(String userId) throws SpaceStorageException {
    try {
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      return identityEntity.getManagerSpaces().getRefs().size();
    }
    catch (NodeNotFoundException e) {
      return 0;
    }
  }

  /**
   * {@inheritDoc}
   */
  public int getEditableSpacesByFilterCount(String userId, SpaceFilter spaceFilter) {
    return getEditableSpacesFilterQuery(userId, spaceFilter).objects().size();
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getEditableSpaces(String userId) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {

      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getManagerSpaces().getRefs().values();

      if (spaceEntities != null) {
        for (SpaceRef spaceRef : spaceEntities) {

          Space space = new Space();
          fillSpaceFromEntity(spaceRef.getSpaceRef(), space);
          spaces.add(space);
        }
      }

    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;

  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getEditableSpaces(String userId, long offset, long limit) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    try {

      int i = 0;
      IdentityEntity identityEntity = identityStorage._findIdentityEntity(OrganizationIdentityProvider.NAME, userId);
      Collection<SpaceRef> spaceEntities = identityEntity.getManagerSpaces().getRefs().values();

      if (spaceEntities != null) {

        Iterator<SpaceRef> it = spaceEntities.iterator();
        _skip(it, offset);

        while (it.hasNext()) {

          SpaceRef spaceRef = it.next();

          Space space = new Space();
          fillSpaceFromEntity(spaceRef.getSpaceRef(), space);
          spaces.add(space);

          if (++i >= limit) {
            return spaces;
          }
        }
      }

    }
    catch (NodeNotFoundException e) {
      LOG.debug(e.getMessage(), e);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getEditableSpacesByFilter(String userId, SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    //
    QueryResult<SpaceEntity> results = getEditableSpacesFilterQuery(userId, spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /*
    All spaces
   */

  /**
   * {@inheritDoc}
   */
  public int getAllSpacesCount() throws SpaceStorageException {

    // TODO : use property to improve the perfs

    return getSpaceRoot().getSpaces().size();

  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getAllSpaces() throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    for (SpaceEntity spaceEntity : getSpaceRoot().getSpaces().values()) {
      Space space = new Space();
      fillSpaceFromEntity(spaceEntity, space);
      spaces.add(space);
    }

    return spaces;

  }

  /**
   * {@inheritDoc}
   */
  public int getAllSpacesByFilterCount(SpaceFilter spaceFilter) {

    if (validateFilter(spaceFilter)) {
      return getSpacesByFilterQuery(spaceFilter).objects().size();
    }
    else {
      return 0;
    }

  }


  /*
    Get spaces
   */

  /**
   * {@inheritDoc}
   */
  public List<Space> getSpaces(long offset, long limit) throws SpaceStorageException {

    List<Space> spaces = new ArrayList<Space>();

    int i = 0;

    Iterator<SpaceEntity> it = getSpaceRoot().getSpaces().values().iterator();
    _skip(it, offset);
    
    while (it.hasNext()) {

      SpaceEntity spaceEntity = it.next();

      Space space = new Space();
      fillSpaceFromEntity(spaceEntity, space);
      spaces.add(space);

      if (++i >= limit) {
        break;
      }

    }

    return spaces;

  }

  /**
   * {@inheritDoc}
   */
  public List<Space> getSpacesByFilter(SpaceFilter spaceFilter, long offset, long limit) {

    List<Space> spaces = new ArrayList<Space>();

    if (!validateFilter(spaceFilter)) {
      return spaces;
    }

    //
    QueryResult<SpaceEntity> results = getSpacesByFilterQuery(spaceFilter).objects(offset, limit);

    while (results.hasNext()) {
      SpaceEntity currentSpace = results.next();
      Space space = new Space();
      fillSpaceFromEntity(currentSpace, space);
      spaces.add(space);
    }

    return spaces;
  }

  /**
   * {@inheritDoc}
   */
  public Space getSpaceById(String id) throws SpaceStorageException {

    try {

      SpaceEntity spaceEntity = _findById(SpaceEntity.class, id);

      Space space = new Space();

      fillSpaceFromEntity(spaceEntity, space);

      return space;

    }
    catch (NodeNotFoundException e) {
      return null;
    }

  }

  /**
   * {@inheritDoc}
   */
  public Space getSpaceByPrettyName(String spacePrettyName) throws SpaceStorageException {

    try {

      SpaceEntity entity = _findByPath(SpaceEntity.class, String.format("/production/soc:spaces/soc:%s", spacePrettyName));

      Space space = new Space();
      fillSpaceFromEntity(entity, space);

      return space;

    }
    catch (NodeNotFoundException e) {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public Space getSpaceByGroupId(String groupId) throws SpaceStorageException {

    // TODO : avoid JCR query ?

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);
    WhereExpression whereExpression = new WhereExpression();

    builder.where(whereExpression.equals(SpaceEntity.groupId, groupId).toString());

    QueryResult<SpaceEntity> result = builder.get().objects();

    if (result.hasNext()) {
      SpaceEntity entity =  result.next();
      Space space = new Space();

      fillSpaceFromEntity(entity, space);

      return space;
    }
    else {
      return null;
    }

  }

  /**
   * {@inheritDoc}
   */
  public Space getSpaceByUrl(String url) throws SpaceStorageException {

    // TODO : avoid JCR query ?

    QueryBuilder<SpaceEntity> builder = getSession().createQueryBuilder(SpaceEntity.class);

    if (url != null) {
      WhereExpression whereExpression = new WhereExpression();
      whereExpression.equals(SpaceEntity.url, url);
      builder.where(whereExpression.toString());
    }

    QueryResult<SpaceEntity> result = builder.get().objects();

    if (result.hasNext()) {

      Space space = new Space();
      SpaceEntity entity =  builder.get().objects().next();

      fillSpaceFromEntity(entity, space);

      return space;

    }
    else {
      return null;
    }

  }

}