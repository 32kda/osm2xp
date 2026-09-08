package com.osm2xp.gui.views.panels;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

public class BindingPanel extends Composite {

	protected final DataBindingContext bindingContext = new DataBindingContext();
	
	public BindingPanel(Composite parent, int style) {
		super(parent, style);
	}
	
	/**
	 * Bind a widget to a bean property
	 * 
	 * @param component
	 * @param bean
	 * @param property
	 */
	protected void bindComponent(Widget component, Object bean, String property) {
		if (component instanceof Spinner) {
			bindingContext.bindValue(WidgetProperties.spinnerSelection().observe((Spinner) component),
					PojoProperties.value(property).observe(bean));
		} else if (component instanceof Button) {
			bindingContext.bindValue(WidgetProperties.buttonSelection().observe((Button) component),
					PojoProperties.value(property).observe(bean));
		} else if (component instanceof Combo || component instanceof Text) {
			bindingContext.bindValue(WidgetProperties.text().observe(component),
					PojoProperties.value(property).observe(bean));
		} 
	}

	protected void bindSpinnerToDouble(Spinner spinner, Object bean, String property, int digits) {
		int factor = (int) Math.pow(10, digits);
		bindingContext.bindValue(WidgetProperties.spinnerSelection().observe(spinner),
				PojoProperties.value(property, Double.class).observe(bean),
				UpdateValueStrategy.create(new Converter<Integer, Double>(int.class, double.class) {

					@Override
					public Double convert(Integer fromObject) {
						return fromObject * 1.0 / factor;
					}
				}), UpdateValueStrategy.create(new Converter<Double, Integer>(double.class, int.class) {

					@Override
					public Integer convert(Double fromObject) {
						return (int) (fromObject * factor);
					}
				}));
	}
	
	protected void bindTextToInt(Text widget, Object bean, String property) {
		bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(widget),
				PojoProperties.value(property, Integer.class).observe(bean),
				UpdateValueStrategy.create(new Converter<String, Integer>(String.class, int.class) {
					
					@Override
					public Integer convert(String fromObject) {
						return Integer.parseInt(fromObject);
					}
				}), UpdateValueStrategy.create(new Converter<Integer, String>(int.class, String.class) {
					
					@Override
					public String convert(Integer fromObject) {
						return fromObject.toString();
					}
				}));
	}
}
