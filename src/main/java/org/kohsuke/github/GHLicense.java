/*
 * The MIT License
 *
 * Copyright (c) 2016, Duncan Dickinson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.kohsuke.github;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The GitHub Preview API's licenses
 */
public class GHLicense {

    protected String key, name, url, html_url, description, category, implementation, body;
    protected Boolean featured;
    protected List<String> required = new ArrayList<>();
    protected List<String> permitted = new ArrayList<>();
    protected List<String> forbidden = new ArrayList<>();

    /**
     * @return a mnemonic for the license
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the license name
     */
    public String getName() {
        return name;
    }

    /**
     * API URL of this object.
     */
    @WithBridgeMethods(value = String.class, adapterMethod = "urlToString")
    public URL getUrl() {
        return GitHub.parseURL(url);
    }

    public URL getHtmlUrl() {
        return GitHub.parseURL(html_url);
    }

    /**
     * @return
     */
    public Boolean isFeatured() {
        return featured;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getImplementation() {
        return implementation;
    }

    public List<String> getRequired() {
        return required;
    }

    public List<String> getPermitted() {
        return permitted;
    }

    public List<String> getForbidden() {
        return forbidden;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "GHLicense{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", html_url='" + html_url + '\'' +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", implementation='" + implementation + '\'' +
                ", body='" + body + '\'' +
                ", featured=" + featured +
                ", required=" + required +
                ", permitted=" + permitted +
                ", forbidden=" + forbidden +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GHLicense ghLicense = (GHLicense) o;

        return getUrl().equals(ghLicense.getUrl());

    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }
}
