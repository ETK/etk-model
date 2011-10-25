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

package org.exoplatform.social.core.storage.cache;

import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.storage.impl.IdentityStorageImpl;
import org.exoplatform.social.core.test.AbstractCoreTest;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:alain.defrance@exoplatform.com">Alain Defrance</a>
 * @version $Revision$
 */
public class CachedActivityStorageTestCase extends AbstractCoreTest {

  private CachedActivityStorage activityStorage;
  private IdentityStorageImpl identityStorage;
  private SocialStorageCacheService cacheService;

  private Identity identity;

  private List<String> tearDownIdentityList;

  @Override
  protected void setUp() throws Exception {
    
    super.setUp();

    //
    activityStorage = (CachedActivityStorage) getContainer().getComponentInstanceOfType(CachedActivityStorage.class);
    identityStorage = (IdentityStorageImpl) getContainer().getComponentInstanceOfType(IdentityStorageImpl.class);
    cacheService = (SocialStorageCacheService) getContainer().getComponentInstanceOfType(SocialStorageCacheService.class);

    //
    cacheService.getActivitiesCache().clearCache();
    cacheService.getActivitiesCountCache().clearCache();
    cacheService.getActivityCache().clearCache();

    //
    identity = new Identity("p", "r");
    identityStorage.saveIdentity(identity);

    //
    tearDownIdentityList = new ArrayList<String>();
    tearDownIdentityList.add(identity.getId());

  }

  @Override
  protected void tearDown() throws Exception {

    for (String id : tearDownIdentityList) {
      identityStorage.deleteIdentity(new Identity(id));
    }
    
    super.tearDown();

  }

  public void testSaveActivity() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("hello");
    activity.setUserId(identity.getId());
    activityStorage.saveActivity(identity, activity);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());

    //
    activityStorage.getActivityFeed(identity, 0, 20);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());

    //
    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity2.setTitle("hello 2");
    activity2.setUserId(identity.getId());
    activityStorage.saveActivity(identity, activity2);

    //
    assertEquals(2, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());

  }

  public void testRemoveActivity() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("hello");
    activity.setUserId(identity.getId());
    activityStorage.saveActivity(identity, activity);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());

    //
    activityStorage.getActivityFeed(identity, 0, 20);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());

    //
    activityStorage.deleteActivity(activity.getId());

    //
    assertEquals(0, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());


  }

  public void testSaveComment() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("hello");
    activity.setUserId(identity.getId());
    activityStorage.saveActivity(identity, activity);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());

    //
    activityStorage.getActivityFeed(identity, 0, 20);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());

    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("comment");
    comment.setUserId(identity.getId());
    activityStorage.saveComment(activity, comment);

    //
    assertEquals(2, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());
    assertEquals(activity.getId(), activityStorage.getActivityFeed(identity, 0, 20).get(0).getId());
    assertEquals(comment.getId(), activityStorage.getActivityFeed(identity, 0, 20).get(0).getReplyToId()[0]);

  }

  public void testRemoveComment() throws Exception {



    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("hello");
    activity.setUserId(identity.getId());
    activityStorage.saveActivity(identity, activity);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(0, cacheService.getActivitiesCache().getCacheSize());

    //
    activityStorage.getActivityFeed(identity, 0, 20);

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());

    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("comment");
    comment.setUserId(identity.getId());
    activityStorage.saveComment(activity, comment);

    //
    assertEquals(2, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());
    assertEquals(activity.getId(), activityStorage.getActivityFeed(identity, 0, 20).get(0).getId());
    assertEquals(comment.getId(), activityStorage.getActivityFeed(identity, 0, 20).get(0).getReplyToId()[0]);

    //
    activityStorage.deleteComment(activity.getId(), comment.getId());

    //
    assertEquals(1, cacheService.getActivityCache().getCacheSize());
    assertEquals(1, cacheService.getActivitiesCache().getCacheSize());
    assertEquals(activity.getId(), activityStorage.getActivityFeed(identity, 0, 20).get(0).getId());
    assertEquals(0, activityStorage.getActivityFeed(identity, 0, 20).get(0).getReplyToId().length);

  }
}
