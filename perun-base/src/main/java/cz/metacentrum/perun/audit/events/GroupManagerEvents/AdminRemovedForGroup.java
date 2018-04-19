package cz.metacentrum.perun.audit.events.GroupManagerEvents;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.User;

public class AdminRemovedForGroup {

    private User user;
    private Group group;
    private String name = this.getClass().getName();
    private String message;

    public String getMessage() {
        return toString();
    }

    public void setMessage(String message) {
        this.message = message;
    }
    public AdminRemovedForGroup(User user, Group group) {
        this.user = user;
        this.group = group;
    }

    public AdminRemovedForGroup() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return user + " was removed from admins of "+ group +".";
    }
}