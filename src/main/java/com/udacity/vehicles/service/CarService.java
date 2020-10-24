package com.udacity.vehicles.service;

import com.udacity.vehicles.client.maps.MapsClient;
import com.udacity.vehicles.client.prices.Price;
import com.udacity.vehicles.client.prices.PriceClient;
import com.udacity.vehicles.domain.Location;
import com.udacity.vehicles.domain.car.Car;
import com.udacity.vehicles.domain.car.CarRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implements the car service create, read, update or delete
 * information about vehicles, as well as gather related
 * location and price data when desired.
 */
@Service
public class CarService {

    private final CarRepository carRepository;
    private MapsClient mapsClient;
    private PriceClient priceClient;

    public CarService(CarRepository carRepository, MapsClient mapsClient, PriceClient priceClient) {
        this.carRepository = carRepository;
        this.mapsClient = mapsClient;
        this.priceClient = priceClient;
    }

    /**
     * Gathers a list of all vehicles
     * @return a list of all vehicles in the CarRepository
     */
    public List<Car> list() {
        List<Car> carList = carRepository.findAll();
        for(Car car : carList) {
            car.setLocation(mapsClient.getAddress(car.getLocation()));
            car.setPrice(priceClient.getPrice(car.getId()));
        }
        return carList;
    }

    /**
     * Gets car information by ID (or throws exception if non-existent)
     * @param id the ID number of the car to gather information on
     * @return the requested car's information, including location and price
     */
    public Car findById(Long id) {

        Car car = carRepository.findById(id).orElseThrow(CarNotFoundException::new);
        String price = priceClient.getPrice(id);
        car.setPrice(price);
        Location location = mapsClient.getAddress(car.getLocation());
        car.setLocation(location);
        return car;
    }

    /**
     * Either creates or updates a vehicle, based on prior existence of car
     * @param car A car object, which can be either new or existing
     * @return the new/updated car is stored in the repository
     */
    public Car save(Car car) {

        //Both Post and Put requests are routed to this method
        //Expect Post requests to have no Id and Put requests to have an associated Id
        //Post requests may or may not have a price provided
        //Put requests vehicles are already in the system and therefore should have a price in the system

        if (car.getId() != null) { //has an Id already = updating an existing car's information
            return carRepository.findById(car.getId())
                    .map(carToBeUpdated -> {
                        carToBeUpdated.setModifiedAt(LocalDateTime.now()); //update the modified time to now
                        carToBeUpdated.setDetails(car.getDetails()); //copy the details over
                        carToBeUpdated.setLocation(car.getLocation()); //copy the location
                        carToBeUpdated.setPrice(car.getPrice());
                        carToBeUpdated.setCondition(car.getCondition());
                        if(carToBeUpdated.getPrice() != null) { //Update the price of the vehicle to the Price Microservice
                            priceClient.postPrice(new Price(carToBeUpdated.getPrice(), carToBeUpdated.getId()));
                        }

                        Car savedCar = carRepository.save(carToBeUpdated);

                        //Populate car with location from new Lon and Lat coordinates
                        savedCar.setLocation(mapsClient.getAddress(carToBeUpdated.getLocation()));
                        //Populate price
                        savedCar.setPrice(priceClient.getPrice(savedCar.getId()));
                        return savedCar;
                    }).orElseThrow(CarNotFoundException::new);
        }

        // New Vehicle
        Car carToReturn = carRepository.save(car);

        // Check if the entered car had a price set. If not, get a quote from the Pricing Microservice
        if(car.getPrice() == null) {
            carToReturn.setPrice(priceClient.setPrice(carToReturn.getId())); //if no price is set, we will get one from the price client
        }

        // Either price was set by Pricing Microservice or User. Now save to Pricing Microservice's repository
        String price = priceClient.postPrice(new Price(car.getPrice(), car.getId()));

        //Get location from location services
        carToReturn.setLocation(mapsClient.getAddress(carToReturn.getLocation()));

        return carToReturn;
    }

    /**
     * Deletes a given car by ID
     * @param id the ID number of the car to delete
     */
    public void delete(Long id) {
        Car car = carRepository.findById(id).orElseThrow(CarNotFoundException::new);
        carRepository.delete(car);
        priceClient.deletePrice(id);
        //TODO: Check and make sure child entity information of this car is also deleted.
    }
}
