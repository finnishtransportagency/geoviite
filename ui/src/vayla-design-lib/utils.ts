/**
 * Excludes falsy values and returns classes as a space separated string.
 *
 * <code>
 *
 * createClassName( 'button', Icon && 'button--with-icon')
 *
 * if "Icon" is falsy, returns "button".
 *
 * if "Icon" is not falsy, returns "button button--with-icon"
 *
 * </code>
 *
 * @param className
 */
export function createClassName(...className: (string | false | undefined | 0)[]): string {
    return className.filter((c) => c).join(' ');
}
