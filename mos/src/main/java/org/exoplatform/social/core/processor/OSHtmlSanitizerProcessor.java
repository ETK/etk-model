/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
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
package org.exoplatform.social.core.processor;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.social.common.xmlprocessor.XMLProcessor;
import org.exoplatform.social.core.BaseActivityProcessorPlugin;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;

public class OSHtmlSanitizerProcessor extends BaseActivityProcessorPlugin {

  private XMLProcessor xmlProcessor;

  public OSHtmlSanitizerProcessor(InitParams params) {
    super(params);
  }

  public void processActivity(ExoSocialActivity activity) {
    if (xmlProcessor == null) {
      xmlProcessor = (XMLProcessor) PortalContainer.getInstance().getComponentInstanceOfType(XMLProcessor.class);
    }
    activity.setTitle((String) xmlProcessor.process(activity.getTitle()));
    activity.setBody((String) xmlProcessor.process(activity.getBody()));
  }

}
