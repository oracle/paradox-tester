package com.webtrends.qa.webtesting

import groovy.transform.InheritConstructors
import groovy.text.*
import groovy.text.markup.*

import org.eclipse.jetty.util.URIUtil
import org.eclipse.jetty.util.resource.PathResource
import org.eclipse.jetty.util.resource.Resource

import java.text.DateFormat

/**
 * The default PathResource lists directories sorted by name.  This sorts them by last modified descending
 */
@InheritConstructors
class TimeSortedResource extends PathResource {
    /* ------------------------------------------------------------ */
    /** Get the resource list as a HTML directory listing sorted by last modified time.
     * @param base The base URL
     * @param parent True if the parent directory should be included
     * @return String of HTML
     */
    @Override
    String getListHTML(String base, boolean parent) {
        def canonicalBase = URIUtil.canonicalPath(base)
        if (canonicalBase == null || !directory) {
            return null
        }

        String[] ls = list()
        if (ls == null) {
            return null
        }

        ls.sort(true) { -addPath(it).lastModified() }

        def title = 'Directory: ' + URIUtil.decodePath(canonicalBase)
        def encodedBase = canonicalBase.replaceAll(/['"<>]/) { "%${it.bytes.encodeHex()}".toUpperCase() }

        MarkupTemplateEngine engine = new MarkupTemplateEngine()
        Template template = engine.createTemplate("""
HTML {
  def title = 'Directory: ' + URIUtil.decodePath(base)
  HEAD {
    LINK(HREF: 'jetty-dir.css', REL: 'stylesheet', TYPE: 'text/css')
    TITLE { yield '$title' }
  }
  BODY {
    H1 { yield '$title' }
    TABLE(BORDER: 0) {
      if (parent) {
        TR {
          TD {
            A(HREF: URIUtil.addPaths(base, '../')) {
              yield 'Parent Directory'
            }
          }
          TD {}
          TD {}
        }
      }
      ls.each { unencodedPath ->
        def item = addPath.call(unencodedPath)
        String path = URIUtil.addPaths('$encodedBase', URIUtil.encodePath(unencodedPath))
        if (item.isDirectory() && !path.endsWith('/')) {
          path += '/'
        }

        TR {
          TD {
            A(HREF: path) {
              yield unencodedPath
              yieldUnescaped '&nbsp;'
            }
          }
          TD(ALIGN: 'right') {
            yield item.length()
            yieldUnescaped ' bytes&nbsp;'
          }
          TD {
            yield formatLastModified(item)
          }
        }
      }
    }
  }
}
""")

        template.make([
                parent: parent,
                base: canonicalBase,
                URIUtil: URIUtil,
                addPath: { String path -> addPath path },
                ls: ls,
                formatLastModified: {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(it.lastModified())
                }
        ]).toString()
    }

    /**
     * Returns the resource contained inside the current resource with the given name.
     * @param path The path segment to add, which is not encoded
     */
    @Override
    Resource addPath(final String subpath) throws IOException, MalformedURLException {
        String canonicalPath = URIUtil.canonicalPath(subpath)
        if (!canonicalPath) {
            throw new MalformedURLException()
        }

        if ('/' == canonicalPath) {
            return this
        }

        new TimeSortedResource(path.resolve("./$canonicalPath").normalize())
    }
}
