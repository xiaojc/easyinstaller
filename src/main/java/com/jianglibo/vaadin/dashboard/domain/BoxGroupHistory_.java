package com.jianglibo.vaadin.dashboard.domain;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value="Dali", date="2016-10-09T18:40:50.851+0800")
@StaticMetamodel(BoxGroupHistory.class)
public class BoxGroupHistory_ extends BaseEntity_ {
	public static volatile SingularAttribute<BoxGroupHistory, Software> software;
	public static volatile SingularAttribute<BoxGroupHistory, BoxGroup> boxGroup;
	public static volatile SingularAttribute<BoxGroupHistory, String> action;
	public static volatile SingularAttribute<BoxGroupHistory, Boolean> forAllBox;
	public static volatile SingularAttribute<BoxGroupHistory, Person> runner;
	public static volatile SingularAttribute<BoxGroupHistory, Boolean> success;
	public static volatile SetAttribute<BoxGroupHistory, BoxHistory> boxHistories;
	public static volatile SingularAttribute<BoxGroupHistory, Boolean> readed;
}
