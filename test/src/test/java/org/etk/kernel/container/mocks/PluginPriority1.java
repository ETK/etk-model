/**
 * Copyright (C) 2003-2008 eXo Platform SAS.
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

package org.etk.kernel.container.mocks;

import org.etk.kernel.container.component.ComponentPlugin;

/**
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: $
 */
public class PluginPriority1 implements ComponentPlugin {

  private String description;
  private String name;
  
  public String getDescription() {
    return description;
//    return "PluginPriority1 description";
  }

  public String getName() {
    return name;
//    return "PluginPriority1";
  }

  public void setDescription(String s) {
    description = s;
  }

  public void setName(String s) {
    name = s;
  }

}
