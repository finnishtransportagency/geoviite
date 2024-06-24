import * as React from 'react';
import { User } from 'user/user-model';
import Card from 'geoviite-design-lib/card/card';
import styles from './user-card.scss';
import { useTranslation } from 'react-i18next';
import { ShowMoreButton } from 'show-more-button/show-more-button';

type UserCardProps = {
    user: User;
};

export const UserCard: React.FC<UserCardProps> = ({ user }: UserCardProps) => {
    const { t } = useTranslation();
    const [showMoreDetails, setShowMoreDetails] = React.useState(false);

    const multipleRoleString = (): string => {
        return user.availableRoles
            .map((role) => {
                const roleTranslation = t(`user-roles.${role.code}`);

                return role.code === user.role.code
                    ? `${roleTranslation} (${t('user-card.selected-role')})`
                    : roleTranslation;
            })
            .join(', ');
    };

    const displayedRoleString =
        user.availableRoles.length > 1 ? multipleRoleString() : t(`user-roles.${user.role.code}`);

    return (
        <Card
            className={styles['user-card']}
            content={
                <React.Fragment>
                    <h3 className={styles['user-card__username-title']}>
                        {t('user-card.my-details')}
                    </h3>
                    <div className={styles['user-card__username']}>{user.details.userName}</div>

                    <div className={styles['user-card__show-more']}>
                        <ShowMoreButton
                            onShowMore={() => setShowMoreDetails(!showMoreDetails)}
                            expanded={showMoreDetails}
                        />
                    </div>

                    {showMoreDetails && (
                        <React.Fragment>
                            <section>
                                <h3 className={styles['user-card__role-title']}>
                                    {t('user-card.role')}
                                </h3>
                                <span>{displayedRoleString}</span>
                            </section>
                            <section>
                                <h3 className={styles['user-card__permissions-title']}>
                                    {t('user-card.permissions')}
                                </h3>
                                <span>
                                    {user.role.privileges.map((priv, index) => (
                                        <span
                                            key={priv.code}
                                            title={t(`privilege.description.${priv.code}`)}>
                                            {t(`privilege.${priv.code}`)}
                                            {index < user.role.privileges.length - 1 && ', '}
                                        </span>
                                    ))}
                                </span>
                            </section>
                        </React.Fragment>
                    )}
                </React.Fragment>
            }></Card>
    );
};
