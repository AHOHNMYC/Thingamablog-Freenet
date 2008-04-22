/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package thingamablog.l10n;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.logging.Level;

import java.util.logging.Logger;
import net.sf.thingamablog.util.io.Closer;
import thingamablog.l10n.SimpleFieldSet;
import net.sf.thingamablog.util.io.FileUtil;


/**
 * This class provides a trivial internationalization framework to a Freenet node.
 *
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 *
 * TODO: Maybe base64 the override file ?
 *
 * comment(mario): for www interface we might detect locale from http requests?
 * for other access (telnet) using system locale would probably be good, but
 * it would be nice to have a command to switch locale on the fly.
 */
public class L10n {
    public static final String CLASS_NAME = "l10n";
    public static final String PREFIX = "thingamablog.l10n.";
    public static final String SUFFIX = ".properties";
    public static final String OVERRIDE_SUFFIX = ".override" + SUFFIX;
    
    public static final String FALLBACK_DEFAULT = "en";
    public static final String[] AVAILABLE_LANGUAGES = { "en", "de", "fr", "es", "ja"};
    private final String selectedLanguage;
    
    private static SimpleFieldSet currentTranslation = null;
    private static SimpleFieldSet fallbackTranslation = null;
    private static L10n currentClass = null;
    
    private static SimpleFieldSet translationOverride;
    private static final Object sync = new Object();
    
    private static Logger logger = Logger.getLogger("net.sf.thingamablog.L10n");
    
    L10n(String selected) {
        selectedLanguage = selected;
        File tmpFile = new File(L10n.PREFIX + selected + L10n.OVERRIDE_SUFFIX);
        
        try {
            if(tmpFile.exists() && tmpFile.canRead() && tmpFile.length() > 0) {
                logger.log(Level.INFO, "Override file detected : let's try to load it");
                translationOverride = SimpleFieldSet.readFrom(tmpFile, false, false);
            } else {
                // try to restore a backup
                File backup = new File(tmpFile.getParentFile(), tmpFile.getName()+".bak");
                if(backup.exists() && backup.length() > 0) {
                    logger.log(Level.INFO, "Override-backup file detected : let's try to load it");
                    translationOverride = SimpleFieldSet.readFrom(backup, false, false);
                }
                translationOverride = null;
            }
            
        } catch (IOException e) {
            translationOverride = null;
            logger.log(Level.SEVERE, "IOError while accessing the file!" + e.getMessage(), e);
        }
        currentTranslation = loadTranslation(selectedLanguage);
        if(currentTranslation == null) {
            logger.log(Level.SEVERE, "The translation file for " + selectedLanguage + " is invalid. The node will load an empty template.");
            currentTranslation = null;
            translationOverride = new SimpleFieldSet(false);
        }
    }
    
    /**
     * Set the default language used by the framework.
     *
     * @param selectedLanguage (2 letter code)
     * @throws MissingResourceException
     */
    public static void setLanguage(String selectedLanguage) throws MissingResourceException {
        synchronized (sync) {
            for(int i=0; i<AVAILABLE_LANGUAGES.length; i++){
                if(selectedLanguage.equalsIgnoreCase(AVAILABLE_LANGUAGES[i])){
                    selectedLanguage = AVAILABLE_LANGUAGES[i];
                    logger.log(Level.INFO, "Changing the current language to : " + selectedLanguage);
                    
                    currentClass = new L10n(selectedLanguage);
                    
                    if(currentTranslation == null) {
                        currentClass = new L10n(FALLBACK_DEFAULT);
                        throw new MissingResourceException("Unable to load the translation file for "+selectedLanguage, "l10n", selectedLanguage);
                    }
                    
                    return;
                }
            }
            
            currentClass = new L10n(FALLBACK_DEFAULT);
            logger.log(Level.SEVERE, "The requested translation is not available!" + selectedLanguage);
            throw new MissingResourceException("The requested translation ("+selectedLanguage+") hasn't been found!", CLASS_NAME, selectedLanguage);
        }
    }
    
    public static void setOverride(String key, String value) {
        key = key.trim();
        value = value.trim();
        synchronized (sync) {
            // Is the override already declared ? if not, create it.
            if(translationOverride == null)
                translationOverride = new SimpleFieldSet(false);
            
            // If there is no need to keep it in the override, remove it...
            // unless the original/default is the same as the translation
            if(("".equals(value) || L10n.getString(key).equals(value)) && !L10n.getDefaultString(key).equals(value)) {
                translationOverride.removeValue(key);
            } else {
                value = value.replaceAll("(\r|\n|\t)+", "");
                
                // Set the value of the override
                translationOverride.putOverwrite(key, value);
                logger.log(Level.INFO, "Got a new translation key: set the Override!");
            }
            
            // Save the file to disk
            _saveTranslationFile();
        }
    }
    
    private static void _saveTranslationFile() {
        FileOutputStream fos = null;
        File finalFile = new File(L10n.PREFIX + L10n.getSelectedLanguage() + L10n.OVERRIDE_SUFFIX);
        
        try {
            // We don't set deleteOnExit on it : if the save operation fails, we want a backup
            File tempFile = new File(finalFile.getParentFile(), finalFile.getName()+".bak");
            logger.log(Level.INFO, "The temporary filename is : " + tempFile);
            
            fos = new FileOutputStream(tempFile);
            L10n.translationOverride.writeTo(fos);
            
            FileUtil.renameTo(tempFile, finalFile);
            logger.log(Level.INFO, "Override file saved successfully!");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while saving the translation override: "+ e.getMessage(), e);
        } finally {
            Closer.close(fos);
        }
    }
    
    /**
     * Return a new copy of the current translation file
     *
     * @return SimpleFieldSet or null
     */
    public static SimpleFieldSet getCurrentLanguageTranslation() {
        synchronized (sync) {
            return (currentTranslation == null ? null : new SimpleFieldSet(currentTranslation));
        }
    }
    
    /**
     * Return a copy of the current translation override if it exists or null
     *
     * @return SimpleFieldSet or null
     */
    public static SimpleFieldSet getOverrideForCurrentLanguageTranslation() {
        synchronized (sync) {
            return (translationOverride == null ? null : new SimpleFieldSet(translationOverride));
        }
    }
    
    /**
     * Return a copy of the default translation file (english one)
     *
     * @return SimpleFieldSet
     */
    public static SimpleFieldSet getDefaultLanguageTranslation() {
        synchronized (sync) {
            if(fallbackTranslation == null)
                fallbackTranslation = loadTranslation(FALLBACK_DEFAULT);
            
            return new SimpleFieldSet(fallbackTranslation);
        }
    }
    
    /**
     * The real meat
     *
     * Same thing as getString(key, false);
     * Ensure it will *always* return a String value.
     *
     * @param key
     * @return the translated string or the default value from the default language or the key if nothing is found
     */
    public static String getString(String key) {
        return getString(key, false);
    }
    
    /**
     * You probably don't want to use that one directly
     * @see getString(String)
     */
    public static String getString(String key, boolean returnNullIfNotFound) {
        String result = null;
        synchronized (sync) {
            if(translationOverride != null)
                result = translationOverride.get(key);
        }
        if(result != null) return result;
        
        synchronized (sync) {
            if(currentTranslation != null)
                result = currentTranslation.get(key);
        }
        if(result != null)
            return result;
        else {
            logger.log(Level.INFO, "The translation for " + key + " hasn't been found ("+getSelectedLanguage()+")! please tell the maintainer.");
            return (returnNullIfNotFound ? null : getDefaultString(key));
        }
    }
    
    /**
     * Return the english translation of the key or the key itself if it doesn't exist.
     *
     * @param key
     * @return String
     */
    public static String getDefaultString(String key) {
        String result = null;
        // We instanciate it only if necessary
        synchronized (sync) {
            if(fallbackTranslation == null)
                fallbackTranslation = loadTranslation(FALLBACK_DEFAULT);
            result = fallbackTranslation.get(key);
        }
        
        if(result != null) {
            return result;
        }
        logger.log(Level.SEVERE, "The default translation for " + key + " hasn't been found!");
        System.err.println("The default translation for " + key + " hasn't been found!");
        new Exception().printStackTrace();
        return key;
    }
    
    /**
     * Allows things like :
     * L10n.getString("testing.test", new String[]{ "test1", "test2" }, new String[] { "a", "b" })
     *
     * @param key
     * @param patterns : a list of patterns wich are matchable from the translation
     * @param values : the values corresponding to the list
     * @return the translated string or the default value from the default language or the key if nothing is found
     */
    public static String getString(String key, String[] patterns, String[] values) {
        assert(patterns.length == values.length);
        String result = getString(key);
        
        for(int i=0; i<patterns.length; i++)
            result = result.replaceAll("\\$\\{"+patterns[i]+"\\}", quoteReplacement(values[i]));
        
        return result;
    }
    
    private static String quoteReplacement(String s) {
        if ((s.indexOf('\\') == -1) && (s.indexOf('$') == -1))
            return s;
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                sb.append('\\');
                sb.append('\\');
            } else if (c == '$') {
                sb.append('\\');
                sb.append('$');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * Return the ISO code of the language used by the framework
     *
     * @return String
     */
    public static String getSelectedLanguage() {
        synchronized (sync) {
            if(currentClass == null) return null;
            return currentClass.selectedLanguage;
        }
    }
    
    /**
     * Load a translation file depending on the given name and using the prefix
     *
     * @param name
     * @return the Properties object or null if not found
     */
    public static SimpleFieldSet loadTranslation(String name) {
        name = PREFIX.replace('.', '/').concat(PREFIX.concat(name.concat(SUFFIX)));
        
        SimpleFieldSet result = null;
        InputStream in = null;
        try {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            
            // Returns null on lookup failures:
            in = loader.getResourceAsStream(name);
            if(in != null)
                result = SimpleFieldSet.readFrom(in, false, false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while loading the l10n file from " + name + " :" + e.getMessage(), e);
            result = null;
        } finally {
            Closer.close(in);
        }
        
        return result;
    }
    
    public static boolean isOverridden(String key) {
        synchronized(sync) {
            if(translationOverride == null) return false;
            return translationOverride.get(key) != null;
        }
    }
    
    public static String getString(String key, String pattern, String value) {
        return getString(key, new String[] { pattern }, new String[] { value }); // FIXME code efficiently!
    }
    
    public static char getMnemonic(String mnemoKey){
        String key = mnemoKey + ".mnemonic";
        String tmp = getString(key);
        char ret;
        if (tmp.length() != 0) {
            ret = tmp.charAt(0);
        } else {
            //Should never happen, but did once...
            System.out.println("Mnemonic key not found : " + key);
            ret = '_';
        }
        return ret;
    }
    
}
